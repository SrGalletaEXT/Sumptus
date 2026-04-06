package com.cris.sumptus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cris.sumptus.data.ExpenseCategory
import com.cris.sumptus.data.ExpenseEntry
import com.cris.sumptus.data.ExpenseRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategorySpendSummary(
    val category: ExpenseCategory,
    val totalCents: Long,
    val share: Float,
)

data class SumptusUiState(
    val monthLabel: String,
    val monthTotalCents: Long,
    val weekTotalCents: Long,
    val averageExpenseCents: Long,
    val expenseCount: Int,
    val recentExpenses: List<ExpenseEntry>,
    val categorySummaries: List<CategorySpendSummary>,
    val insight: String,
    val feedbackMessage: String? = null,
) {
    companion object {
        fun empty(): SumptusUiState = SumptusUiState(
            monthLabel = "",
            monthTotalCents = 0L,
            weekTotalCents = 0L,
            averageExpenseCents = 0L,
            expenseCount = 0,
            recentExpenses = emptyList(),
            categorySummaries = emptyList(),
            insight = "",
            feedbackMessage = null,
        )
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExpenseRepository(application.applicationContext)
    private val locale = Locale("es", "ES")
    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SumptusUiState> = combine(repository.expenses, feedback) { expenses, message ->
        buildUiState(expenses, message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SumptusUiState.empty(),
    )

    fun addExpense(
        title: String,
        amountInput: String,
        category: ExpenseCategory,
        notes: String,
    ): Boolean {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            feedback.value = "Escribe un concepto antes de guardar."
            return false
        }

        val amountCents = parseAmountToCents(amountInput)
        if (amountCents == null || amountCents <= 0) {
            feedback.value = "Introduce un importe valido, por ejemplo 12,50."
            return false
        }

        viewModelScope.launch {
            repository.addExpense(
                ExpenseEntry(
                    title = cleanTitle,
                    amountCents = amountCents,
                    category = category,
                    notes = notes.trim(),
                    occurredOn = LocalDate.now(),
                ),
            )
            feedback.value = "Gasto guardado en local."
        }
        return true
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            feedback.value = "Movimiento eliminado."
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    private fun buildUiState(
        expenses: List<ExpenseEntry>,
        feedbackMessage: String?,
    ): SumptusUiState {
        val currentMonth = YearMonth.now()
        val monthExpenses = expenses.filter { YearMonth.from(it.occurredOn) == currentMonth }
        val today = LocalDate.now()
        val weekExpenses = expenses.filter { !it.occurredOn.isBefore(today.minusDays(6)) }

        val monthTotal = monthExpenses.sumOf { it.amountCents }
        val weekTotal = weekExpenses.sumOf { it.amountCents }
        val averageExpense = if (expenses.isEmpty()) 0L else expenses.sumOf { it.amountCents } / expenses.size
        val categorySummaries = buildCategorySummaries(monthExpenses, monthTotal)
        val topCategory = categorySummaries.firstOrNull()

        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        val monthLabel = currentMonth.atDay(1).format(monthFormatter).replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }

        val insight = when {
            expenses.isEmpty() -> "Empieza por registrar los gastos diarios. En una semana ya tendremos patrones utiles."
            topCategory == null -> "Todavia no hay gastos este mes. Usa la vista Nuevo para anotar el siguiente."
            topCategory.share >= 0.45f -> "Tu mayor presion este mes esta en ${topCategory.category.label.lowercase(locale)}."
            else -> "Tu gasto esta repartido. Revisa el historial para detectar suscripciones o picos puntuales."
        }

        return SumptusUiState(
            monthLabel = monthLabel,
            monthTotalCents = monthTotal,
            weekTotalCents = weekTotal,
            averageExpenseCents = averageExpense,
            expenseCount = expenses.size,
            recentExpenses = expenses.take(12),
            categorySummaries = categorySummaries,
            insight = insight,
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

    private fun parseAmountToCents(rawInput: String): Long? {
        val normalized = rawInput
            .trim()
            .replace("€", "")
            .replace(" ", "")
            .replace(DecimalFormatSymbols.getInstance(locale).groupingSeparator.toString(), "")
            .replace(',', '.')

        val amount = normalized.toBigDecimalOrNull() ?: return null
        return amount
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .longValueExact()
    }
}
