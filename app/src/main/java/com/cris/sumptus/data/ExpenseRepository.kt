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
import org.json.JSONObject

private val expensesKey = stringPreferencesKey("expenses_json")
private val migrationCompleteKey = booleanPreferencesKey("room_migration_complete")
private val categoriesKey = stringPreferencesKey("categories_json")
private val extraEntriesKey = stringPreferencesKey("extra_entries_json")
private val balanceAdjustmentCentsKey = longPreferencesKey("balance_adjustment_cents")
private val defaultCategoriesRemovedKey = booleanPreferencesKey("default_categories_removed")
private val retiredDefaultCategories = setOf(
    "Hogar",
    "Comida",
    "Transporte",
    "Ocio",
    "Salud",
    "Suscripciones",
    "Otros",
)
private const val backupVersion = 1

private data class BackupData(
    val expenses: List<ExpenseEntry>,
    val incomes: List<ExtraEntry>,
    val categories: List<String>,
    val balanceAdjustmentCents: Long,
)

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
        }
    }

    val expenses: Flow<List<ExpenseEntry>> = expenseDao.observeExpenses()

    val categories: Flow<List<String>> = preferencesFlow
        .map { preferences -> decodeCategories(preferences[categoriesKey]) }
        .distinctUntilChanged()

    val extraEntries: Flow<List<ExtraEntry>> = preferencesFlow
        .map { preferences -> decodeExtraEntries(preferences[extraEntriesKey]) }
        .distinctUntilChanged()

    val balanceAdjustmentCents: Flow<Long> = preferencesFlow
        .map { preferences -> preferences[balanceAdjustmentCentsKey] ?: 0L }
        .distinctUntilChanged()

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

    suspend fun setBalanceAdjustmentCents(amountCents: Long) {
        dataStore.edit { preferences ->
            preferences[balanceAdjustmentCentsKey] = amountCents
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
        }
    }

    suspend fun exportBackup(): String {
        val preferences = preferencesFlow.first()
        return JSONObject()
            .put("version", backupVersion)
            .put("expenses", JSONArray(expenseDao.getExpenses().map { it.toJson() }))
            .put("incomes", JSONArray(decodeExtraEntries(preferences[extraEntriesKey]).map { it.toJson() }))
            .put("categories", JSONArray(decodeCategories(preferences[categoriesKey])))
            .put("balanceAdjustmentCents", preferences[balanceAdjustmentCentsKey] ?: 0L)
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
            preferences[extraEntriesKey] = encodeExtraEntries(backup.incomes)
            preferences[balanceAdjustmentCentsKey] = backup.balanceAdjustmentCents
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

    private fun encodeCategories(categories: List<String>): String = JSONArray(
        categories
            .map(::normalizeCategoryName)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() },
    ).toString()

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

    private fun decodeBackup(rawBackup: String): BackupData? = runCatching {
        val root = JSONObject(rawBackup)
        check(root.getInt("version") == backupVersion)

        val expenseItems = root.getJSONArray("expenses")
        val expenses = decodeExpenses(expenseItems.toString())
        check(expenses.size == expenseItems.length())

        val incomeItems = root.getJSONArray("incomes")
        val incomes = decodeExtraEntries(incomeItems.toString())
        check(incomes.size == incomeItems.length())

        BackupData(
            expenses = expenses,
            incomes = incomes,
            categories = decodeCategories(root.getJSONArray("categories").toString()),
            balanceAdjustmentCents = root.getLong("balanceAdjustmentCents"),
        )
    }.getOrNull()
}
