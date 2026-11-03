package com.cris.sumptus.data

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import com.cris.sumptus.i18n.AppLanguage
import org.json.JSONObject

private val expensesKey = stringPreferencesKey("expenses_json")
private val migrationCompleteKey = booleanPreferencesKey("room_migration_complete")
private val categoriesKey = stringPreferencesKey("categories_json")
private val categoryColorsKey = stringPreferencesKey("category_colors_json")
private val darkThemeKey = booleanPreferencesKey("dark_theme")
private val languageKey = stringPreferencesKey("language")
private val extraEntriesKey = stringPreferencesKey("extra_entries_json")
private val balanceAdjustmentCentsKey = longPreferencesKey("balance_adjustment_cents")
private val monthlyBalanceAdjustmentsKey = stringPreferencesKey("monthly_balance_adjustments_json")
private val budgetChangesKey = stringPreferencesKey("budget_changes_json")
private val generalExtraLimitCentsKey = longPreferencesKey("general_extra_limit_cents")
private val generalExtraLimitChangesKey = stringPreferencesKey("general_extra_limit_changes_json")
private val monthlyBalancesMigratedKey = booleanPreferencesKey("monthly_balances_migrated")
private val defaultCategoriesRemovedKey = booleanPreferencesKey("default_categories_removed")
private val investmentTransfersKey = stringPreferencesKey("investment_transfers_json")
private val investmentsKey = stringPreferencesKey("investments_json")
private val investmentAllocationsKey = stringPreferencesKey("investment_allocations_json")
private val balanceAdjustmentsCarryoverMigratedKey = booleanPreferencesKey("balance_adjustments_carryover_migrated")
private val generalPotGainCentsKey = longPreferencesKey("general_pot_gain_cents")
private val retiredDefaultCategories = setOf(
    "Hogar",
    "Comida",
    "Transporte",
    "Ocio",
    "Salud",
    "Suscripciones",
    "Otros",
)
private const val backupVersion = 3

/** Backups up to this version store monthly balance adjustments where every month started at 0. */
private const val lastIndependentMonthBackupVersion = 2
private const val minSupportedBackupVersion = 1

private data class BackupData(
    val version: Int,
    val expenses: List<ExpenseEntry>,
    val incomes: List<ExtraEntry>,
    val categories: List<String>,
    val categoryColors: Map<String, Long>,
    val monthlyBalanceAdjustments: Map<String, Long>,
    val budgetChanges: Map<String, Long>,
    val generalExtraLimitChanges: Map<String, Long>,
    val legacyGeneralExtraLimitCents: Long?,
    val legacyBalanceAdjustmentCents: Long?,
    val investmentTransfers: List<InvestmentTransfer>,
    val investments: List<Investment>,
    val investmentAllocations: List<InvestmentAllocation>,
    val generalPotGainCents: Long,
)

/**
 * Converts monthly balance adjustments from the old model, where every month started at 0 (so an
 * adjustment held the whole opening balance), to the carry-over model, where a month starts with
 * the previous month's ending balance (so an adjustment is only the difference from it).
 *
 * Months that had an adjustment keep exactly the ending balance they showed before; months
 * without one now start from the previous month's balance instead of 0.
 */
internal fun convertIndependentMonthAdjustmentsToCarryover(
    adjustments: Map<String, Long>,
    expenses: List<ExpenseEntry>,
    incomes: List<ExtraEntry>,
): Map<String, Long> {
    if (adjustments.isEmpty()) return adjustments

    val netByMonth = mutableMapOf<YearMonth, Long>()
    expenses.forEach { netByMonth.merge(YearMonth.from(it.occurredOn), -it.amountCents, Long::plus) }
    incomes.forEach { netByMonth.merge(YearMonth.from(it.occurredOn), it.amountCents, Long::plus) }
    val adjustmentMonths = adjustments.keys.map(YearMonth::parse)
    val lastMonth = adjustmentMonths.max()
    var month = (adjustmentMonths + netByMonth.keys).min()

    val converted = mutableMapOf<String, Long>()
    var previousEnding = 0L
    while (month <= lastMonth) {
        val net = netByMonth[month] ?: 0L
        val oldAdjustment = adjustments[month.toString()]
        previousEnding = if (oldAdjustment != null) {
            converted[month.toString()] = oldAdjustment - previousEnding
            oldAdjustment + net
        } else {
            previousEnding + net
        }
        month = month.plusMonths(1)
    }
    return converted
}

internal fun categoryColorKey(categoryName: String): String =
    normalizeCategoryName(categoryName).lowercase(Locale.ROOT)

internal fun categoryBudgetKey(month: YearMonth, categoryName: String): String =
    "$month|${categoryColorKey(categoryName)}"

class ExpenseRepository(context: Context) {
    private val expenseDao = SumptusDatabase.getInstance(context).expenseDao()
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(context.filesDir, "sumptus.preferences_pb") },
    )
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferencesFlow = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    init {
        repositoryScope.launch {
            migrateLegacyExpensesIfNeeded()
            removeDefaultCategoriesOnce()
            migrateBalanceAdjustmentsToMonths()
            migrateGeneralExtraLimitToMonths()
            migrateBalanceAdjustmentsToCarryover()
        }
    }

    val expenses: Flow<List<ExpenseEntry>> = expenseDao.observeExpenses()

    val categories: Flow<List<String>> = preferencesFlow
        .map { preferences -> decodeCategories(preferences[categoriesKey]) }
        .distinctUntilChanged()

    val categoryColors: Flow<Map<String, Long>> = preferencesFlow
        .map { preferences -> decodeCategoryColors(preferences[categoryColorsKey]) }
        .distinctUntilChanged()

    val language: Flow<AppLanguage> = preferencesFlow
        .map { preferences -> AppLanguage.fromTag(preferences[languageKey]) ?: AppLanguage.systemDefault() }
        .distinctUntilChanged()

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { preferences -> preferences[languageKey] = language.tag }
    }

    val darkTheme: Flow<Boolean> = preferencesFlow
        .map { preferences -> preferences[darkThemeKey] ?: false }
        .distinctUntilChanged()

    val extraEntries: Flow<List<ExtraEntry>> = preferencesFlow
        .map { preferences -> decodeExtraEntries(preferences[extraEntriesKey]) }
        .distinctUntilChanged()

    val monthlyBalanceAdjustments: Flow<Map<String, Long>> = preferencesFlow
        .map { preferences -> decodeMonthlyBalanceAdjustments(preferences[monthlyBalanceAdjustmentsKey]) }
        .distinctUntilChanged()

    val budgetChanges: Flow<Map<String, Long>> = preferencesFlow
        .map { preferences -> decodeBudgetChanges(preferences[budgetChangesKey]) }
        .distinctUntilChanged()

    val generalExtraLimitChanges: Flow<Map<String, Long>> = preferencesFlow
        .map { preferences -> decodeGeneralExtraLimitChanges(preferences[generalExtraLimitChangesKey]) }
        .distinctUntilChanged()

    val investmentTransfers: Flow<List<InvestmentTransfer>> = preferencesFlow
        .map { preferences -> decodeInvestmentTransfers(preferences[investmentTransfersKey]) }
        .distinctUntilChanged()

    val investments: Flow<List<Investment>> = preferencesFlow
        .map { preferences -> decodeInvestments(preferences[investmentsKey]) }
        .distinctUntilChanged()

    val investmentAllocations: Flow<List<InvestmentAllocation>> = preferencesFlow
        .map { preferences -> decodeInvestmentAllocations(preferences[investmentAllocationsKey]) }
        .distinctUntilChanged()

    val generalPotGainCents: Flow<Long> = preferencesFlow
        .map { preferences -> preferences[generalPotGainCentsKey] ?: 0L }
        .distinctUntilChanged()

    suspend fun upsertInvestmentTransfer(transfer: InvestmentTransfer) {
        dataStore.edit { preferences ->
            val transfers = decodeInvestmentTransfers(preferences[investmentTransfersKey]).toMutableList()
            val existingIndex = transfers.indexOfFirst { it.id == transfer.id }
            if (existingIndex >= 0) transfers[existingIndex] = transfer else transfers += transfer
            preferences[investmentTransfersKey] = encodeInvestmentTransfers(transfers)
        }
    }

    suspend fun deleteInvestmentTransfer(transferId: String) {
        dataStore.edit { preferences ->
            val transfers = decodeInvestmentTransfers(preferences[investmentTransfersKey]).filterNot { it.id == transferId }
            preferences[investmentTransfersKey] = encodeInvestmentTransfers(transfers)
        }
    }

    suspend fun addInvestment(investment: Investment) {
        dataStore.edit { preferences ->
            val investments = decodeInvestments(preferences[investmentsKey]).toMutableList()
            investments += investment
            preferences[investmentsKey] = encodeInvestments(investments)
        }
    }

    suspend fun setInvestmentGain(investmentId: String, gainCents: Long) {
        dataStore.edit { preferences ->
            val investments = decodeInvestments(preferences[investmentsKey]).map { investment ->
                if (investment.id == investmentId) investment.copy(gainCents = gainCents) else investment
            }
            preferences[investmentsKey] = encodeInvestments(investments)
        }
    }

    /**
     * Removes an investment as if it had been sold: its allocations disappear (so the invested
     * money is back in the general pot) and its gain is added to the pot's gain, so the pot ends
     * up holding the investment's full current value.
     */
    suspend fun deleteInvestment(investmentId: String) {
        dataStore.edit { preferences ->
            val allInvestments = decodeInvestments(preferences[investmentsKey])
            val removed = allInvestments.firstOrNull { it.id == investmentId } ?: return@edit
            val investments = allInvestments.filterNot { it.id == investmentId }
            preferences[generalPotGainCentsKey] = (preferences[generalPotGainCentsKey] ?: 0L) + removed.gainCents
            preferences[investmentsKey] = encodeInvestments(investments)
            val allocations = decodeInvestmentAllocations(preferences[investmentAllocationsKey])
                .filterNot { it.investmentId == investmentId }
            preferences[investmentAllocationsKey] = encodeInvestmentAllocations(allocations)
        }
    }

    suspend fun upsertInvestmentAllocation(allocation: InvestmentAllocation) {
        dataStore.edit { preferences ->
            val allocations = decodeInvestmentAllocations(preferences[investmentAllocationsKey]).toMutableList()
            val existingIndex = allocations.indexOfFirst { it.id == allocation.id }
            if (existingIndex >= 0) allocations[existingIndex] = allocation else allocations += allocation
            preferences[investmentAllocationsKey] = encodeInvestmentAllocations(allocations)
        }
    }

    suspend fun deleteInvestmentAllocation(allocationId: String) {
        dataStore.edit { preferences ->
            val allocations = decodeInvestmentAllocations(preferences[investmentAllocationsKey]).filterNot { it.id == allocationId }
            preferences[investmentAllocationsKey] = encodeInvestmentAllocations(allocations)
        }
    }

    suspend fun setGeneralPotGainCents(gainCents: Long) {
        dataStore.edit { preferences -> preferences[generalPotGainCentsKey] = gainCents }
    }

    suspend fun upsertExpense(expense: ExpenseEntry) {
        expenseDao.upsertExpense(expense)
    }

    suspend fun deleteExpense(expenseId: String) {
        expenseDao.deleteExpenseById(expenseId)
    }

    suspend fun upsertExtraEntry(entry: ExtraEntry) {
        dataStore.edit { preferences ->
            val extras = decodeExtraEntries(preferences[extraEntriesKey]).toMutableList()
            val existingIndex = extras.indexOfFirst { it.id == entry.id }
            if (existingIndex >= 0) {
                extras[existingIndex] = entry
            } else {
                extras += entry
            }
            preferences[extraEntriesKey] = encodeExtraEntries(extras)
        }
    }

    suspend fun deleteExtraEntry(entryId: String) {
        dataStore.edit { preferences ->
            val filteredEntries = decodeExtraEntries(preferences[extraEntriesKey])
                .filterNot { it.id == entryId }
            preferences[extraEntriesKey] = encodeExtraEntries(filteredEntries)
        }
    }

    suspend fun setMonthlyBalanceAdjustmentCents(month: YearMonth, amountCents: Long) {
        dataStore.edit { preferences ->
            val adjustments = decodeMonthlyBalanceAdjustments(preferences[monthlyBalanceAdjustmentsKey]).toMutableMap()
            adjustments[month.toString()] = amountCents
            preferences[monthlyBalanceAdjustmentsKey] = encodeMonthlyBalanceAdjustments(adjustments)
        }
    }

    suspend fun setCategoryBudget(month: YearMonth, categoryName: String, amountCents: Long) {
        val key = categoryBudgetKey(month, categoryName)
        if (key.endsWith("|")) return

        dataStore.edit { preferences ->
            val budgetChanges = decodeBudgetChanges(preferences[budgetChangesKey]).toMutableMap()
            budgetChanges[key] = amountCents
            preferences[budgetChangesKey] = encodeBudgetChanges(budgetChanges)
        }
    }

    suspend fun setGeneralExtraLimitCents(month: YearMonth, amountCents: Long?) {
        dataStore.edit { preferences ->
            val changes = decodeGeneralExtraLimitChanges(preferences[generalExtraLimitChangesKey]).toMutableMap()
            if (amountCents == null) changes.remove(month.toString()) else changes[month.toString()] = amountCents
            if (changes.isEmpty()) preferences.remove(generalExtraLimitChangesKey)
            else preferences[generalExtraLimitChangesKey] = encodeGeneralExtraLimitChanges(changes)
        }
    }

    suspend fun deleteCategory(categoryName: String) {
        dataStore.edit { preferences ->
            val categories = decodeCategories(preferences[categoriesKey])
                .filterNot { it.equals(categoryName, ignoreCase = true) }
            if (categories.isEmpty()) {
                preferences.remove(categoriesKey)
            } else {
                preferences[categoriesKey] = encodeCategories(categories)
            }
            val categoryColors = decodeCategoryColors(preferences[categoryColorsKey]).toMutableMap()
            categoryColors.remove(categoryColorKey(categoryName))
            if (categoryColors.isEmpty()) {
                preferences.remove(categoryColorsKey)
            } else {
                preferences[categoryColorsKey] = encodeCategoryColors(categoryColors)
            }
            val budgetChanges = decodeBudgetChanges(preferences[budgetChangesKey])
                .filterNot { (key, _) -> key.endsWith("|${categoryColorKey(categoryName)}") }
            if (budgetChanges.isEmpty()) {
                preferences.remove(budgetChangesKey)
            } else {
                preferences[budgetChangesKey] = encodeBudgetChanges(budgetChanges)
            }
        }
    }

    suspend fun setCategoryColor(categoryName: String, colorArgb: Long) {
        val key = categoryColorKey(categoryName)
        if (key.isBlank()) return

        dataStore.edit { preferences ->
            val categoryColors = decodeCategoryColors(preferences[categoryColorsKey]).toMutableMap()
            categoryColors[key] = colorArgb
            preferences[categoryColorsKey] = encodeCategoryColors(categoryColors)
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[darkThemeKey] = enabled
        }
    }

    suspend fun exportBackup(): String {
        val preferences = preferencesFlow.first()
        return JSONObject()
            .put("version", backupVersion)
            .put("expenses", JSONArray(expenseDao.getExpenses().map { it.toJson() }))
            .put("incomes", JSONArray(decodeExtraEntries(preferences[extraEntriesKey]).map { it.toJson() }))
            .put("categories", JSONArray(decodeCategories(preferences[categoriesKey])))
            .put("categoryColors", JSONObject(decodeCategoryColors(preferences[categoryColorsKey])))
            .put("monthlyBalanceAdjustments", JSONObject(decodeMonthlyBalanceAdjustments(preferences[monthlyBalanceAdjustmentsKey])))
            .put("budgetChanges", JSONObject(decodeBudgetChanges(preferences[budgetChangesKey])))
            .put("generalExtraLimitChanges", JSONObject(decodeGeneralExtraLimitChanges(preferences[generalExtraLimitChangesKey])))
            .put("balanceAdjustmentCents", 0L)
            .put("investmentTransfers", JSONArray(decodeInvestmentTransfers(preferences[investmentTransfersKey]).map { it.toJson() }))
            .put("investments", JSONArray(decodeInvestments(preferences[investmentsKey]).map { it.toJson() }))
            .put("investmentAllocations", JSONArray(decodeInvestmentAllocations(preferences[investmentAllocationsKey]).map { it.toJson() }))
            .put("generalPotGainCents", preferences[generalPotGainCentsKey] ?: 0L)
            .toString()
    }

    suspend fun importBackup(rawBackup: String): Boolean {
        val backup = decodeBackup(rawBackup) ?: return false
        expenseDao.replaceExpenses(backup.expenses)
        dataStore.edit { preferences ->
            if (backup.categories.isEmpty()) {
                preferences.remove(categoriesKey)
            } else {
                preferences[categoriesKey] = encodeCategories(backup.categories)
            }
            if (backup.categoryColors.isEmpty()) {
                preferences.remove(categoryColorsKey)
            } else {
                preferences[categoryColorsKey] = encodeCategoryColors(backup.categoryColors)
            }
            preferences[extraEntriesKey] = encodeExtraEntries(backup.incomes)
            val storedAdjustments = backup.monthlyBalanceAdjustments.ifEmpty {
                legacyMonthlyBalanceAdjustments(
                    backup.legacyBalanceAdjustmentCents ?: 0L,
                    backup.expenses,
                    backup.incomes,
                )
            }
            val adjustments = if (backup.version <= lastIndependentMonthBackupVersion) {
                convertIndependentMonthAdjustmentsToCarryover(storedAdjustments, backup.expenses, backup.incomes)
            } else {
                storedAdjustments
            }
            if (adjustments.isEmpty()) {
                preferences.remove(monthlyBalanceAdjustmentsKey)
            } else {
                preferences[monthlyBalanceAdjustmentsKey] = encodeMonthlyBalanceAdjustments(adjustments)
            }
            if (backup.budgetChanges.isEmpty()) {
                preferences.remove(budgetChangesKey)
            } else {
                preferences[budgetChangesKey] = encodeBudgetChanges(backup.budgetChanges)
            }
            val extraLimitChanges = backup.generalExtraLimitChanges.ifEmpty {
                backup.legacyGeneralExtraLimitCents?.let { mapOf(YearMonth.now().toString() to it) }.orEmpty()
            }
            if (extraLimitChanges.isEmpty()) {
                preferences.remove(generalExtraLimitChangesKey)
            } else {
                preferences[generalExtraLimitChangesKey] = encodeGeneralExtraLimitChanges(extraLimitChanges)
            }
            preferences[investmentTransfersKey] = encodeInvestmentTransfers(backup.investmentTransfers)
            preferences[investmentsKey] = encodeInvestments(backup.investments)
            preferences[investmentAllocationsKey] = encodeInvestmentAllocations(backup.investmentAllocations)
            preferences[generalPotGainCentsKey] = backup.generalPotGainCents
            preferences.remove(balanceAdjustmentCentsKey)
            preferences[monthlyBalancesMigratedKey] = true
            preferences[balanceAdjustmentsCarryoverMigratedKey] = true
            preferences[defaultCategoriesRemovedKey] = true
        }
        return true
    }

    suspend fun addCategory(categoryName: String) {
        dataStore.edit { preferences ->
            val categories = decodeCategories(preferences[categoriesKey])
                .toMutableList()
            val normalizedCategory = normalizeCategoryName(categoryName)
            if (normalizedCategory.isBlank()) {
                return@edit
            }
            if (categories.none { it.equals(normalizedCategory, ignoreCase = true) }) {
                categories += normalizedCategory
                preferences[categoriesKey] = encodeCategories(categories)
            }
        }
    }

    private suspend fun migrateLegacyExpensesIfNeeded() {
        val preferences = preferencesFlow.first()

        if (preferences[migrationCompleteKey] == true) {
            return
        }

        val legacyExpenses = decodeExpenses(preferences[expensesKey].orEmpty())
        val existingExpenses = expenseDao.countExpenses()

        if (existingExpenses == 0 && legacyExpenses.isNotEmpty()) {
            expenseDao.upsertExpenses(legacyExpenses)
        }

        dataStore.edit { mutablePreferences ->
            mutablePreferences[migrationCompleteKey] = true
            mutablePreferences.remove(expensesKey)
        }
    }

    private suspend fun removeDefaultCategoriesOnce() {
        dataStore.edit { preferences ->
            if (preferences[defaultCategoriesRemovedKey] == true) {
                return@edit
            }

            val categories = decodeCategories(preferences[categoriesKey])
                .filterNot { it in retiredDefaultCategories }
            if (categories.isEmpty()) {
                preferences.remove(categoriesKey)
            } else {
                preferences[categoriesKey] = encodeCategories(categories)
            }
            preferences[defaultCategoriesRemovedKey] = true
        }
    }

    private suspend fun migrateBalanceAdjustmentsToMonths() {
        val preferences = preferencesFlow.first()
        if (preferences[monthlyBalancesMigratedKey] == true) {
            return
        }

        val legacyBalance = preferences[balanceAdjustmentCentsKey] ?: 0L
        val expenses = expenseDao.getExpenses()
        val incomes = decodeExtraEntries(preferences[extraEntriesKey])
        val adjustments = legacyMonthlyBalanceAdjustments(legacyBalance, expenses, incomes)

        dataStore.edit { mutablePreferences ->
            if (adjustments.isEmpty()) {
                mutablePreferences.remove(monthlyBalanceAdjustmentsKey)
            } else {
                mutablePreferences[monthlyBalanceAdjustmentsKey] = encodeMonthlyBalanceAdjustments(adjustments)
            }
            mutablePreferences.remove(balanceAdjustmentCentsKey)
            mutablePreferences[monthlyBalancesMigratedKey] = true
        }
    }

    private suspend fun migrateBalanceAdjustmentsToCarryover() {
        val preferences = preferencesFlow.first()
        if (preferences[balanceAdjustmentsCarryoverMigratedKey] == true) return

        val adjustments = decodeMonthlyBalanceAdjustments(preferences[monthlyBalanceAdjustmentsKey])
        val converted = convertIndependentMonthAdjustmentsToCarryover(
            adjustments,
            expenseDao.getExpenses(),
            decodeExtraEntries(preferences[extraEntriesKey]),
        )
        dataStore.edit { mutablePreferences ->
            if (converted.isEmpty()) {
                mutablePreferences.remove(monthlyBalanceAdjustmentsKey)
            } else {
                mutablePreferences[monthlyBalanceAdjustmentsKey] = encodeMonthlyBalanceAdjustments(converted)
            }
            mutablePreferences[balanceAdjustmentsCarryoverMigratedKey] = true
        }
    }

    private suspend fun migrateGeneralExtraLimitToMonths() {
        val preferences = preferencesFlow.first()
        val legacyLimit = preferences[generalExtraLimitCentsKey] ?: return
        val changes = decodeGeneralExtraLimitChanges(preferences[generalExtraLimitChangesKey]).toMutableMap()
        if (changes.isEmpty()) {
            changes[YearMonth.now().toString()] = legacyLimit
        }

        dataStore.edit { mutablePreferences ->
            mutablePreferences[generalExtraLimitChangesKey] = encodeGeneralExtraLimitChanges(changes)
            mutablePreferences.remove(generalExtraLimitCentsKey)
        }
    }

    private fun decodeExpenses(rawJson: String): List<ExpenseEntry> {
        if (rawJson.isBlank()) {
            return emptyList()
        }

        return runCatching {
            val items = JSONArray(rawJson)
            buildList {
                for (index in 0 until items.length()) {
                    ExpenseEntry.fromJson(items.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList()).sortedWith(
            compareByDescending<ExpenseEntry> { it.occurredOn }
                .thenByDescending { it.createdAtEpochMillis },
        )
    }

    private fun decodeCategories(rawJson: String?): List<String> {
        if (rawJson.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            val items = JSONArray(rawJson)
            buildList<String> {
                for (index in 0 until items.length()) {
                    val categoryName = normalizeCategoryName(items.optString(index))
                    if (categoryName.isNotBlank() && none { existing -> existing.equals(categoryName, ignoreCase = true) }) {
                        add(categoryName)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeCategoryColors(rawJson: String?): Map<String, Long> {
        if (rawJson.isNullOrBlank()) {
            return emptyMap()
        }

        return runCatching {
            val values = JSONObject(rawJson)
            buildMap {
                val keys = values.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    val key = categoryColorKey(rawKey)
                    if (key.isNotBlank()) {
                        put(key, values.getLong(rawKey))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeCategories(categories: List<String>): String = JSONArray(
        categories
            .map(::normalizeCategoryName)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() },
    ).toString()

    private fun encodeCategoryColors(categoryColors: Map<String, Long>): String = JSONObject(categoryColors).toString()

    private fun decodeMonthlyBalanceAdjustments(rawJson: String?): Map<String, Long> {
        if (rawJson.isNullOrBlank()) return emptyMap()

        return runCatching {
            val values = JSONObject(rawJson)
            buildMap {
                val keys = values.keys()
                while (keys.hasNext()) {
                    val rawMonth = keys.next()
                    val month = YearMonth.parse(rawMonth)
                    put(month.toString(), values.getLong(rawMonth))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeMonthlyBalanceAdjustments(adjustments: Map<String, Long>): String = JSONObject(adjustments).toString()

    private fun decodeGeneralExtraLimitChanges(rawJson: String?): Map<String, Long> =
        decodeMonthlyBalanceAdjustments(rawJson)

    private fun encodeGeneralExtraLimitChanges(changes: Map<String, Long>): String =
        encodeMonthlyBalanceAdjustments(changes)

    private fun decodeBudgetChanges(rawJson: String?): Map<String, Long> {
        if (rawJson.isNullOrBlank()) return emptyMap()

        return runCatching {
            val values = JSONObject(rawJson)
            buildMap {
                val keys = values.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    val (month, category) = rawKey.split("|", limit = 2)
                    put(categoryBudgetKey(YearMonth.parse(month), category), values.getLong(rawKey))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeBudgetChanges(changes: Map<String, Long>): String = JSONObject(changes).toString()

    private fun legacyMonthlyBalanceAdjustments(
        legacyBalanceAdjustmentCents: Long,
        expenses: List<ExpenseEntry>,
        incomes: List<ExtraEntry>,
    ): Map<String, Long> {
        if (legacyBalanceAdjustmentCents == 0L && expenses.isEmpty() && incomes.isEmpty()) return emptyMap()

        val month = YearMonth.now()
        val allBalance = legacyBalanceAdjustmentCents + incomes.sumOf { it.amountCents } - expenses.sumOf { it.amountCents }
        val monthNet = incomes.filter { YearMonth.from(it.occurredOn) == month }.sumOf { it.amountCents } -
            expenses.filter { YearMonth.from(it.occurredOn) == month }.sumOf { it.amountCents }
        return mapOf(month.toString() to allBalance - monthNet)
    }

    private fun decodeExtraEntries(rawJson: String?): List<ExtraEntry> {
        if (rawJson.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            val items = JSONArray(rawJson)
            buildList {
                for (index in 0 until items.length()) {
                    ExtraEntry.fromJson(items.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList()).sortedWith(
            compareByDescending<ExtraEntry> { it.occurredOn }
                .thenByDescending { it.createdAtEpochMillis },
        )
    }

    private fun encodeExtraEntries(entries: List<ExtraEntry>): String = JSONArray(
        entries
            .sortedWith(
                compareByDescending<ExtraEntry> { it.occurredOn }
                    .thenByDescending { it.createdAtEpochMillis },
            )
            .map { it.toJson() },
    ).toString()

    private fun <T> decodeJsonList(rawJson: String?, parse: (JSONObject) -> T?): List<T> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val items = JSONArray(rawJson)
            buildList {
                for (index in 0 until items.length()) {
                    parse(items.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeInvestmentTransfers(rawJson: String?): List<InvestmentTransfer> =
        decodeJsonList(rawJson, InvestmentTransfer::fromJson)

    private fun encodeInvestmentTransfers(transfers: List<InvestmentTransfer>): String =
        JSONArray(transfers.map { it.toJson() }).toString()

    private fun decodeInvestments(rawJson: String?): List<Investment> =
        decodeJsonList(rawJson, Investment::fromJson)

    private fun encodeInvestments(investments: List<Investment>): String =
        JSONArray(investments.map { it.toJson() }).toString()

    private fun decodeInvestmentAllocations(rawJson: String?): List<InvestmentAllocation> =
        decodeJsonList(rawJson, InvestmentAllocation::fromJson)

    private fun encodeInvestmentAllocations(allocations: List<InvestmentAllocation>): String =
        JSONArray(allocations.map { it.toJson() }).toString()

    private fun decodeBackup(rawBackup: String): BackupData? = runCatching {
        val root = JSONObject(rawBackup)
        val version = root.getInt("version")
        check(version in minSupportedBackupVersion..backupVersion)

        val expenseItems = root.getJSONArray("expenses")
        val expenses = decodeExpenses(expenseItems.toString())
        check(expenses.size == expenseItems.length())

        val incomeItems = root.getJSONArray("incomes")
        val incomes = decodeExtraEntries(incomeItems.toString())
        check(incomes.size == incomeItems.length())

        BackupData(
            version = version,
            expenses = expenses,
            incomes = incomes,
            categories = decodeCategories(root.getJSONArray("categories").toString()),
            categoryColors = decodeCategoryColors(root.optJSONObject("categoryColors")?.toString()),
            monthlyBalanceAdjustments = decodeMonthlyBalanceAdjustments(root.optJSONObject("monthlyBalanceAdjustments")?.toString()),
            budgetChanges = decodeBudgetChanges(root.optJSONObject("budgetChanges")?.toString()),
            generalExtraLimitChanges = decodeGeneralExtraLimitChanges(root.optJSONObject("generalExtraLimitChanges")?.toString()),
            legacyGeneralExtraLimitCents = root.takeIf {
                !it.has("generalExtraLimitChanges") && it.has("generalExtraLimitCents") && !it.isNull("generalExtraLimitCents")
            }?.getLong("generalExtraLimitCents"),
            legacyBalanceAdjustmentCents = root.takeIf { it.has("balanceAdjustmentCents") }?.getLong("balanceAdjustmentCents"),
            investmentTransfers = decodeInvestmentTransfers(root.optJSONArray("investmentTransfers")?.toString()),
            investments = decodeInvestments(root.optJSONArray("investments")?.toString()),
            investmentAllocations = decodeInvestmentAllocations(root.optJSONArray("investmentAllocations")?.toString()),
            generalPotGainCents = root.optLong("generalPotGainCents", 0L),
        )
    }.getOrNull()
}
