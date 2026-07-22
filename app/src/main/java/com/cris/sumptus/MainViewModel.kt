package com.cris.sumptus

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cris.sumptus.data.ExpenseEntry
import com.cris.sumptus.data.ExpenseRepository
import com.cris.sumptus.data.ExtraEntry
import com.cris.sumptus.data.IncomeType
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

data class SumptusUiState(
    val selectedMonth: YearMonth,
    val monthLabel: String,
    val monthExpenseTotalCents: Long,
    val monthIncomeTotalCents: Long,
    val monthNetCents: Long,
    val currentBalanceCents: Long,
    val balanceAdjustmentCents: Long,
    val previousMonthExpenseTotalCents: Long,
    val previousMonthNetCents: Long,
    val expenseDeltaFromPreviousMonthCents: Long,
    val categories: List<String>,
    val categorySummaries: List<CategorySpendSummary>,
    val historyEntries: List<TimelineEntry>,
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
            previousMonthExpenseTotalCents = 0L,
            previousMonthNetCents = 0L,
            expenseDeltaFromPreviousMonthCents = 0L,
            categories = emptyList(),
            categorySummaries = emptyList(),
            historyEntries = emptyList(),
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
        val extraEntries: List<ExtraEntry>,
        val balanceAdjustmentCents: Long,
        val selectedMonth: YearMonth,
    )

    private data class EntryInputs(
        val expenses: List<ExpenseEntry>,
        val categories: List<String>,
        val extraEntries: List<ExtraEntry>,
    )

    private val entryInputs = combine(
        repository.expenses,
        repository.categories,
        repository.extraEntries,
    ) { expenses, categories, extraEntries ->
        EntryInputs(expenses, categories, extraEntries)
    }

    private val summaryInputs = combine(
        entryInputs,
        repository.balanceAdjustmentCents,
        selectedMonth,
    ) { entries, balanceAdjustmentCents, month ->
        SummaryInputs(
            expenses = entries.expenses,
            categories = entries.categories,
            extraEntries = entries.extraEntries,
            balanceAdjustmentCents = balanceAdjustmentCents,
            selectedMonth = month,
        )
    }

    val uiState: StateFlow<SumptusUiState> = combine(summaryInputs, feedback) { inputs, message ->
        buildUiState(
            expenses = inputs.expenses,
            storedCategories = inputs.categories,
            extraEntries = inputs.extraEntries,
            balanceAdjustmentCents = inputs.balanceAdjustmentCents,
            selectedMonth = inputs.selectedMonth,
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
            repository.setBalanceAdjustmentCents(targetBalanceCents - calculatedWithoutAdjustment)
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
        extraEntries: List<ExtraEntry>,
        balanceAdjustmentCents: Long,
        selectedMonth: YearMonth,
        feedbackMessage: String?,
    ): SumptusUiState {
        val monthExpenses = expenses.filter { YearMonth.from(it.occurredOn) == selectedMonth }
        val monthExtras = extraEntries.filter { YearMonth.from(it.occurredOn) == selectedMonth }
        val previousMonth = selectedMonth.minusMonths(1)
        val previousMonthExpenseTotal = expenses
            .filter { YearMonth.from(it.occurredOn) == previousMonth }
            .sumOf { it.amountCents }
        val previousMonthExtraTotal = extraEntries
            .filter { YearMonth.from(it.occurredOn) == previousMonth }
            .sumOf { it.amountCents }

        val monthExpenseTotal = monthExpenses.sumOf { it.amountCents }
        val monthExtraTotal = monthExtras.sumOf { it.amountCents }
        val monthIncomeTotal = monthExtraTotal
        val previousMonthIncomeTotal = previousMonthExtraTotal
        val allExpenseTotal = expenses.sumOf { it.amountCents }
        val allExtraTotal = extraEntries.sumOf { it.amountCents }

        return SumptusUiState(
            selectedMonth = selectedMonth,
            monthLabel = formatMonth(selectedMonth),
            monthExpenseTotalCents = monthExpenseTotal,
            monthIncomeTotalCents = monthIncomeTotal,
            monthNetCents = monthIncomeTotal - monthExpenseTotal,
            currentBalanceCents = balanceAdjustmentCents + allExtraTotal - allExpenseTotal,
            balanceAdjustmentCents = balanceAdjustmentCents,
            previousMonthExpenseTotalCents = previousMonthExpenseTotal,
            previousMonthNetCents = previousMonthIncomeTotal - previousMonthExpenseTotal,
            expenseDeltaFromPreviousMonthCents = monthExpenseTotal - previousMonthExpenseTotal,
            categories = storedCategories,
            categorySummaries = buildCategorySummaries(monthExpenses, monthExpenseTotal),
            historyEntries = buildHistoryEntries(monthExpenses, monthExtras),
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
