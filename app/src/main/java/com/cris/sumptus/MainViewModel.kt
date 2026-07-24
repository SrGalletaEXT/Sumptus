package com.cris.sumptus

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cris.sumptus.data.ExpenseEntry
import com.cris.sumptus.data.ExpenseRepository
import com.cris.sumptus.data.ExtraEntry
import com.cris.sumptus.data.IncomeType
import com.cris.sumptus.data.categoryColorKey
import com.cris.sumptus.data.normalizeCategoryName
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategorySpendSummary(
    val category: String,
    val totalCents: Long,
    val share: Float,
)

data class MonthExpenseComparison(
    val month: YearMonth,
    val totalCents: Long,
    val categories: List<CategorySpendSummary>,
)

data class RecentExpense(
    val expense: ExpenseEntry,
    val balanceAfterCents: Long,
)

data class CategoryBudgetSummary(
    val category: String,
    val baseCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
)

data class BudgetOverview(
    val carryoverCents: Long,
    val baseCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val categories: List<CategoryBudgetSummary>,
) {
    companion object {
        fun empty() = BudgetOverview(0L, 0L, 0L, 0L, emptyList())
    }
}

sealed interface TimelineEntry {
    val id: String
    val amountCents: Long
    val occurredOn: LocalDate
    val createdAtEpochMillis: Long

    data class ExpenseItem(val expense: ExpenseEntry) : TimelineEntry {
        override val id: String = expense.id
        override val amountCents: Long = expense.amountCents
        override val occurredOn: LocalDate = expense.occurredOn
        override val createdAtEpochMillis: Long = expense.createdAtEpochMillis
    }

    data class ExtraItem(val extra: ExtraEntry) : TimelineEntry {
        override val id: String = extra.id
        override val amountCents: Long = extra.amountCents
        override val occurredOn: LocalDate = extra.occurredOn
        override val createdAtEpochMillis: Long = extra.createdAtEpochMillis
    }
}

internal fun buildRecentExpenses(
    expenses: List<ExpenseEntry>,
    extraEntries: List<ExtraEntry>,
    monthlyBalanceAdjustments: Map<String, Long>,
): List<RecentExpense> {
    val balances = mutableMapOf<String, Long>()

    (expenses.map { TimelineEntry.ExpenseItem(it) } + extraEntries.map { TimelineEntry.ExtraItem(it) })
        .groupBy { YearMonth.from(it.occurredOn) }
        .forEach { (month, entries) ->
            var balance = monthlyBalanceAdjustments[month.toString()] ?: 0L
            entries.forEach { entry ->
                balance += if (entry is TimelineEntry.ExtraItem) entry.amountCents else -entry.amountCents
            }

            entries.sortedWith(compareByDescending<TimelineEntry> { it.createdAtEpochMillis }.thenByDescending { it.id })
                .forEach { entry ->
                    when (entry) {
                        is TimelineEntry.ExpenseItem -> {
                            balances[entry.id] = balance
                            balance += entry.amountCents
                        }
                        is TimelineEntry.ExtraItem -> balance -= entry.amountCents
                    }
                }
        }

    return expenses
        .sortedWith(compareByDescending<ExpenseEntry> { it.createdAtEpochMillis }.thenByDescending { it.id })
        .map { expense -> RecentExpense(expense, balances.getValue(expense.id)) }
}

internal fun buildBudgetOverview(
    expenses: List<ExpenseEntry>,
    categories: List<String>,
    budgetChanges: Map<String, Long>,
    selectedMonth: YearMonth,
): BudgetOverview {
    val categoryChanges = categories.associateWith { category ->
        val categoryKey = categoryColorKey(category)
        budgetChanges.mapNotNull { (key, amountCents) ->
            val parts = key.split("|", limit = 2)
            if (parts.size == 2 && parts[1] == categoryKey) YearMonth.parse(parts[0]) to amountCents else null
        }.sortedBy { it.first }
    }
    val firstMonth = categoryChanges.values.flatten().minOfOrNull { it.first } ?: return BudgetOverview.empty()
    if (firstMonth > selectedMonth) return BudgetOverview.empty()

    var carryoverCents = 0L
    var month = firstMonth
    while (month <= selectedMonth) {
        val bases = categoryChanges.mapNotNull { (category, changes) ->
            changes.lastOrNull { it.first <= month }?.second?.let { category to it }
        }.toMap()
        val baseCents = bases.values.sum()
        val spentCents = expenses.filter { YearMonth.from(it.occurredOn) == month }.sumOf { it.amountCents }
        val remainingCents = baseCents + carryoverCents - spentCents
        if (month == selectedMonth) {
            return BudgetOverview(
                carryoverCents = carryoverCents,
                baseCents = baseCents,
                spentCents = spentCents,
                remainingCents = remainingCents,
                categories = bases.map { (category, categoryBaseCents) ->
                    val categorySpentCents = expenses
                        .filter { YearMonth.from(it.occurredOn) == month && categoryColorKey(it.category) == categoryColorKey(category) }
                        .sumOf { it.amountCents }
                    CategoryBudgetSummary(
                        category = category,
                        baseCents = categoryBaseCents,
                        spentCents = categorySpentCents,
                        remainingCents = categoryBaseCents - categorySpentCents,
                    )
                }.sortedBy { it.category },
            )
        }
        carryoverCents = remainingCents
        month = month.plusMonths(1)
    }
    return BudgetOverview.empty()
}

data class SumptusUiState(
    val selectedMonth: YearMonth,
    val monthLabel: String,
    val monthExpenseTotalCents: Long,
    val monthIncomeTotalCents: Long,
    val monthNetCents: Long,
    val currentBalanceCents: Long,
    val balanceAdjustmentCents: Long,
    val categories: List<String>,
    val categoryColors: Map<String, Long>,
    val budgetOverview: BudgetOverview,
    val lastThreeMonthExpenses: List<MonthExpenseComparison>,
    val recentExpenses: List<RecentExpense>,
    val historyEntries: List<TimelineEntry>,
    val isDarkTheme: Boolean,
    val feedbackMessage: String? = null,
) {
    companion object {
        fun empty(): SumptusUiState = SumptusUiState(
            selectedMonth = YearMonth.now(),
            monthLabel = "",
            monthExpenseTotalCents = 0L,
            monthIncomeTotalCents = 0L,
            monthNetCents = 0L,
            currentBalanceCents = 0L,
            balanceAdjustmentCents = 0L,
            categories = emptyList(),
            categoryColors = emptyMap(),
            budgetOverview = BudgetOverview.empty(),
            lastThreeMonthExpenses = emptyList(),
            recentExpenses = emptyList(),
            historyEntries = emptyList(),
            isDarkTheme = false,
            feedbackMessage = null,
        )
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExpenseRepository(application.applicationContext)
    private val locale = Locale.forLanguageTag("es-ES")
    private val feedback = MutableStateFlow<String?>(null)
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private data class SummaryInputs(
        val expenses: List<ExpenseEntry>,
        val categories: List<String>,
        val categoryColors: Map<String, Long>,
        val extraEntries: List<ExtraEntry>,
        val budgetChanges: Map<String, Long>,
        val monthlyBalanceAdjustments: Map<String, Long>,
        val selectedMonth: YearMonth,
    )

    private data class EntryInputs(
        val expenses: List<ExpenseEntry>,
        val categories: List<String>,
        val categoryColors: Map<String, Long>,
        val extraEntries: List<ExtraEntry>,
        val budgetChanges: Map<String, Long>,
    )

    private val entryInputs = combine(
        repository.expenses,
        repository.categories,
        repository.categoryColors,
        repository.extraEntries,
        repository.budgetChanges,
    ) { expenses, categories, categoryColors, extraEntries, budgetChanges ->
        EntryInputs(expenses, categories, categoryColors, extraEntries, budgetChanges)
    }

    private val summaryInputs = combine(
        entryInputs,
        repository.monthlyBalanceAdjustments,
        selectedMonth,
    ) { entries, monthlyBalanceAdjustments, month ->
        SummaryInputs(
            expenses = entries.expenses,
            categories = entries.categories,
            categoryColors = entries.categoryColors,
            extraEntries = entries.extraEntries,
            budgetChanges = entries.budgetChanges,
            monthlyBalanceAdjustments = monthlyBalanceAdjustments,
            selectedMonth = month,
        )
    }

    val uiState: StateFlow<SumptusUiState> = combine(summaryInputs, feedback, repository.darkTheme) { inputs, message, isDarkTheme ->
        buildUiState(
            expenses = inputs.expenses,
            storedCategories = inputs.categories,
            categoryColors = inputs.categoryColors,
            extraEntries = inputs.extraEntries,
            budgetChanges = inputs.budgetChanges,
            monthlyBalanceAdjustments = inputs.monthlyBalanceAdjustments,
            selectedMonth = inputs.selectedMonth,
            isDarkTheme = isDarkTheme,
            feedbackMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SumptusUiState.empty(),
    )

    fun selectMonth(month: YearMonth) {
        selectedMonth.value = month
    }

    fun showPreviousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        val currentMonth = YearMonth.now()
        if (selectedMonth.value < currentMonth) {
            selectedMonth.value = selectedMonth.value.plusMonths(1)
        }
    }

    fun saveExpense(
        existingExpense: ExpenseEntry?,
        amountInput: String,
        category: String,
        notes: String,
        occurredOnInput: String,
    ): Boolean {
        val normalizedCategory = normalizeCategoryName(category)
        if (normalizedCategory.isBlank()) {
            feedback.value = "Elige categoría."
            return false
        }

        val amountCents = parseAmountToCents(amountInput)
        if (amountCents == null || amountCents <= 0) {
            feedback.value = "Importe no válido."
            return false
        }

        val occurredOn = parseExpenseInputDate(occurredOnInput)
        if (occurredOn == null) {
            feedback.value = "Fecha no válida."
            return false
        }

        selectedMonth.value = YearMonth.from(occurredOn)
        val trimmedNotes = notes.trim()
        val entryTitle = trimmedNotes.lineSequence().firstOrNull().orEmpty().ifBlank { normalizedCategory }

        viewModelScope.launch {
            repository.upsertExpense(
                ExpenseEntry(
                    id = existingExpense?.id ?: UUID.randomUUID().toString(),
                    title = entryTitle,
                    amountCents = amountCents,
                    category = normalizedCategory,
                    notes = trimmedNotes,
                    occurredOn = occurredOn,
                    createdAtEpochMillis = existingExpense?.createdAtEpochMillis ?: System.currentTimeMillis(),
                ),
            )
            feedback.value = if (existingExpense == null) "Gasto guardado." else "Gasto actualizado."
        }
        return true
    }

    fun saveIncome(
        existingExtra: ExtraEntry?,
        amountInput: String,
        notes: String,
        occurredOnInput: String,
        type: IncomeType,
    ): Boolean {
        val amountCents = parseAmountToCents(amountInput)
        if (amountCents == null || amountCents <= 0) {
            feedback.value = "Importe no válido."
            return false
        }

        val occurredOn = parseExpenseInputDate(occurredOnInput)
        if (occurredOn == null) {
            feedback.value = "Fecha no válida."
            return false
        }

        selectedMonth.value = YearMonth.from(occurredOn)
        viewModelScope.launch {
            repository.upsertExtraEntry(
                ExtraEntry(
                    id = existingExtra?.id ?: UUID.randomUUID().toString(),
                    amountCents = amountCents,
                    notes = notes.trim(),
                    occurredOn = occurredOn,
                    type = type,
                    createdAtEpochMillis = existingExtra?.createdAtEpochMillis ?: System.currentTimeMillis(),
                ),
            )
            val label = if (type == IncomeType.SALARY) "Sueldo" else "Extra"
            feedback.value = if (existingExtra == null) "$label guardado." else "$label actualizado."
        }
        return true
    }

    fun setCurrentBalance(amountInput: String): Boolean {
        val targetBalanceCents = parseAmountToCents(amountInput)
        if (targetBalanceCents == null) {
            feedback.value = "Saldo no válido."
            return false
        }

        val state = uiState.value
        val calculatedWithoutAdjustment = state.currentBalanceCents - state.balanceAdjustmentCents
        viewModelScope.launch {
            repository.setMonthlyBalanceAdjustmentCents(
                state.selectedMonth,
                targetBalanceCents - calculatedWithoutAdjustment,
            )
            feedback.value = "Saldo ajustado."
        }
        return true
    }

    fun addCategory(categoryName: String): Boolean {
        val normalizedCategory = normalizeCategoryName(categoryName)
        if (normalizedCategory.isBlank()) {
            feedback.value = "Escribe una categoría."
            return false
        }

        if (uiState.value.categories.any { it.equals(normalizedCategory, ignoreCase = true) }) {
            feedback.value = "La categoría ya existe."
            return false
        }

        viewModelScope.launch {
            repository.addCategory(normalizedCategory)
            feedback.value = "Categoría añadida."
        }
        return true
    }

    fun deleteCategory(categoryName: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryName)
            feedback.value = "Categoría eliminada."
        }
    }

    fun setCategoryColor(categoryName: String, colorArgb: Long) {
        viewModelScope.launch {
            repository.setCategoryColor(categoryName, colorArgb)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(enabled)
        }
    }

    fun setCategoryBudget(category: String, amountInput: String): Boolean {
        val amountCents = parseAmountToCents(amountInput)
        if (amountCents == null || amountCents <= 0L) {
            feedback.value = "Presupuesto no válido."
            return false
        }

        val state = uiState.value
        viewModelScope.launch {
            repository.setCategoryBudget(state.selectedMonth, category, amountCents)
            feedback.value = "Presupuesto guardado."
        }
        return true
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val backup = repository.exportBackup()
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(backup.toByteArray(Charsets.UTF_8))
                } ?: error("No se pudo crear el archivo.")
            }.onSuccess {
                feedback.value = "Datos exportados."
            }.onFailure {
                feedback.value = "No se pudo exportar."
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val rawBackup = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("No se pudo abrir el archivo.")
                repository.importBackup(rawBackup)
            }.onSuccess { imported ->
                feedback.value = if (imported) "Datos importados." else "Copia no válida."
            }.onFailure {
                feedback.value = "No se pudo importar."
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            feedback.value = "Gasto eliminado."
        }
    }

    fun deleteIncome(income: ExtraEntry) {
        viewModelScope.launch {
            repository.deleteExtraEntry(income.id)
            feedback.value = if (income.type == IncomeType.SALARY) "Sueldo eliminado." else "Extra eliminado."
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    private fun buildUiState(
        expenses: List<ExpenseEntry>,
        storedCategories: List<String>,
        categoryColors: Map<String, Long>,
        extraEntries: List<ExtraEntry>,
        budgetChanges: Map<String, Long>,
        monthlyBalanceAdjustments: Map<String, Long>,
        selectedMonth: YearMonth,
        isDarkTheme: Boolean,
        feedbackMessage: String?,
    ): SumptusUiState {
        val monthExpenses = expenses.filter { YearMonth.from(it.occurredOn) == selectedMonth }
        val monthExtras = extraEntries.filter { YearMonth.from(it.occurredOn) == selectedMonth }
        val monthExpenseTotal = monthExpenses.sumOf { it.amountCents }
        val monthExtraTotal = monthExtras.sumOf { it.amountCents }
        val monthIncomeTotal = monthExtraTotal
        val balanceAdjustmentCents = monthlyBalanceAdjustments[selectedMonth.toString()] ?: 0L
        val currentBalanceCents = balanceAdjustmentCents + monthIncomeTotal - monthExpenseTotal

        return SumptusUiState(
            selectedMonth = selectedMonth,
            monthLabel = formatMonth(selectedMonth),
            monthExpenseTotalCents = monthExpenseTotal,
            monthIncomeTotalCents = monthIncomeTotal,
            monthNetCents = monthIncomeTotal - monthExpenseTotal,
            currentBalanceCents = currentBalanceCents,
            balanceAdjustmentCents = balanceAdjustmentCents,
            categories = storedCategories,
            categoryColors = categoryColors,
            budgetOverview = buildBudgetOverview(expenses, storedCategories, budgetChanges, selectedMonth),
            lastThreeMonthExpenses = buildLastThreeMonthExpenses(expenses, selectedMonth),
            recentExpenses = buildRecentExpenses(expenses, extraEntries, monthlyBalanceAdjustments),
            historyEntries = buildHistoryEntries(monthExpenses, monthExtras),
            isDarkTheme = isDarkTheme,
            feedbackMessage = feedbackMessage,
        )
    }

    private fun buildCategorySummaries(
        monthExpenses: List<ExpenseEntry>,
        monthTotal: Long,
    ): List<CategorySpendSummary> {
        if (monthExpenses.isEmpty() || monthTotal <= 0L) {
            return emptyList()
        }

        return monthExpenses
            .groupBy { it.category }
            .map { (category, entries) ->
                val total = entries.sumOf { it.amountCents }
                CategorySpendSummary(
                    category = category,
                    totalCents = total,
                    share = total.toFloat() / monthTotal.toFloat(),
                )
            }
            .sortedByDescending { it.totalCents }
    }

    private fun buildLastThreeMonthExpenses(
        expenses: List<ExpenseEntry>,
        selectedMonth: YearMonth,
    ): List<MonthExpenseComparison> = (2L downTo 0L).map { offset ->
        val month = selectedMonth.minusMonths(offset)
        val monthExpenses = expenses.filter { YearMonth.from(it.occurredOn) == month }
        val totalCents = monthExpenses.sumOf { it.amountCents }
        MonthExpenseComparison(
            month = month,
            totalCents = totalCents,
            categories = buildCategorySummaries(monthExpenses, totalCents),
        )
    }

    private fun buildHistoryEntries(
        expenses: List<ExpenseEntry>,
        extraEntries: List<ExtraEntry>,
    ): List<TimelineEntry> = buildList {
        addAll(expenses.map { TimelineEntry.ExpenseItem(it) })
        addAll(extraEntries.map { TimelineEntry.ExtraItem(it) })
    }.sortedWith(
        compareByDescending<TimelineEntry> { it.occurredOn }
            .thenByDescending { it.createdAtEpochMillis },
    )

    private fun formatMonth(month: YearMonth): String {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        return month.atDay(1).format(formatter).replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(locale)
            } else {
                char.toString()
            }
        }
    }
}
