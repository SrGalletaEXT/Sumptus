package com.cris.sumptus

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cris.sumptus.data.ExpenseEntry
import com.cris.sumptus.data.ExpenseRepository
import com.cris.sumptus.data.ExtraEntry
import com.cris.sumptus.data.IncomeType
import com.cris.sumptus.data.Investment
import com.cris.sumptus.data.InvestmentAllocation
import com.cris.sumptus.data.InvestmentTransfer
import com.cris.sumptus.data.InvestmentValuation
import com.cris.sumptus.data.InvestmentInterest
import com.cris.sumptus.data.GENERAL_POT_ID
import com.cris.sumptus.data.categoryColorKey
import com.cris.sumptus.i18n.AppLanguage
import com.cris.sumptus.i18n.Strings
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

/** One entry of the "latest activity" list: an expense, an income or a move to/from investments. */
data class RecentExpense(
    val expense: ExpenseEntry?,
    val balanceAfterCents: Long,
    val transfer: InvestmentTransfer? = null,
    val extra: ExtraEntry? = null,
) {
    val id: String get() = expense?.id ?: extra?.id ?: transfer!!.id
    val amountCents: Long get() = expense?.amountCents ?: extra?.amountCents ?: transfer!!.amountCents
    val occurredOn: LocalDate get() = expense?.occurredOn ?: extra?.occurredOn ?: transfer!!.occurredOn
}

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

/**
 * End-of-month balance for every month from the earliest known activity (expense, income or
 * manual adjustment) through [throughMonth], carrying the previous month's ending balance
 * forward into the next one. A month with no activity and no adjustment simply keeps the
 * balance unchanged, so the general balance never silently resets to 0 when the month rolls over.
 */
internal fun buildMonthEndingBalances(
    expenses: List<ExpenseEntry>,
    extraEntries: List<ExtraEntry>,
    monthlyBalanceAdjustments: Map<String, Long>,
    throughMonth: YearMonth,
    investmentTransfers: List<InvestmentTransfer> = emptyList(),
): Map<YearMonth, Long> {
    val expensesByMonth = expenses.groupBy { YearMonth.from(it.occurredOn) }
    val extrasByMonth = extraEntries.groupBy { YearMonth.from(it.occurredOn) }
    val transfersByMonth = investmentTransfers.groupBy { YearMonth.from(it.occurredOn) }
    val knownMonths = expensesByMonth.keys + extrasByMonth.keys + transfersByMonth.keys +
        monthlyBalanceAdjustments.keys.map(YearMonth::parse)
    val firstMonth = (knownMonths + throughMonth).minOrNull() ?: return emptyMap()

    val balances = LinkedHashMap<YearMonth, Long>()
    var balance = 0L
    var month = firstMonth
    while (month <= throughMonth) {
        val monthExpenseTotal = expensesByMonth[month]?.sumOf { it.amountCents } ?: 0L
        val monthExtraTotal = extrasByMonth[month]?.sumOf { it.amountCents } ?: 0L
        val monthTransferTotal = transfersByMonth[month]?.sumOf { it.amountCents } ?: 0L
        val adjustment = monthlyBalanceAdjustments[month.toString()] ?: 0L
        balance += adjustment + monthExtraTotal - monthExpenseTotal - monthTransferTotal
        balances[month] = balance
        month = month.plusMonths(1)
    }
    return balances
}

/** A movement that changes the Saldo, used to rebuild the balance right after each one. */
private data class BalanceMovement(
    val id: String,
    val occurredOn: LocalDate,
    val createdAtEpochMillis: Long,
    val deltaCents: Long,
)

/** Newest first by the date the user gave; same-day entries by when they were added. */
private fun <T> recentFirst(
    date: (T) -> LocalDate,
    createdAt: (T) -> Long,
    id: (T) -> String,
): Comparator<T> = compareByDescending(date).thenByDescending(createdAt).thenByDescending(id)

internal fun buildRecentExpenses(
    expenses: List<ExpenseEntry>,
    extraEntries: List<ExtraEntry>,
    monthlyBalanceAdjustments: Map<String, Long>,
    investmentTransfers: List<InvestmentTransfer> = emptyList(),
): List<RecentExpense> {
    if (expenses.isEmpty() && extraEntries.isEmpty() && investmentTransfers.isEmpty()) return emptyList()

    val movementsByMonth = buildList {
        expenses.forEach { add(BalanceMovement(it.id, it.occurredOn, it.createdAtEpochMillis, -it.amountCents)) }
        extraEntries.forEach { add(BalanceMovement(it.id, it.occurredOn, it.createdAtEpochMillis, it.amountCents)) }
        investmentTransfers.forEach { add(BalanceMovement(it.id, it.occurredOn, it.createdAtEpochMillis, -it.amountCents)) }
    }.groupBy { YearMonth.from(it.occurredOn) }
    val lastMonth = (movementsByMonth.keys + monthlyBalanceAdjustments.keys.map(YearMonth::parse)).max()
    val monthEndingBalances = buildMonthEndingBalances(
        expenses,
        extraEntries,
        monthlyBalanceAdjustments,
        lastMonth,
        investmentTransfers,
    )

    val balances = mutableMapOf<String, Long>()
    movementsByMonth.forEach { (month, movements) ->
        var balance = monthEndingBalances.getValue(month)

        movements.sortedWith(recentFirst(BalanceMovement::occurredOn, BalanceMovement::createdAtEpochMillis, BalanceMovement::id))
            .forEach { movement ->
                balances[movement.id] = balance
                balance -= movement.deltaCents
            }
    }

    return (
        expenses.map { RecentExpense(it, balances.getValue(it.id)) to it.createdAtEpochMillis } +
            extraEntries.map { RecentExpense(null, balances.getValue(it.id), extra = it) to it.createdAtEpochMillis } +
            investmentTransfers.map { RecentExpense(null, balances.getValue(it.id), transfer = it) to it.createdAtEpochMillis }
        )
        .sortedWith(recentFirst({ it.first.occurredOn }, { it.second }, { it.first.id }))
        .map { it.first }
}

internal fun buildBudgetOverview(
    expenses: List<ExpenseEntry>,
    categories: List<String>,
    budgetChanges: Map<String, Long>,
    generalExtraLimitChanges: Map<String, Long>,
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
        val budgetedCategoryKeys = bases.keys.map(::categoryColorKey).toSet()
        val spentCents = expenses
            .filter { YearMonth.from(it.occurredOn) == month && categoryColorKey(it.category) in budgetedCategoryKeys }
            .sumOf { it.amountCents }
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
        carryoverCents = effectiveGeneralExtraLimit(generalExtraLimitChanges, month)
            ?.let { remainingCents.coerceAtMost(it) }
            ?: remainingCents
        month = month.plusMonths(1)
    }
    return BudgetOverview.empty()
}

internal fun effectiveGeneralExtraLimit(changes: Map<String, Long>, month: YearMonth): Long? =
    changes.mapNotNull { (changeMonth, amountCents) ->
        YearMonth.parse(changeMonth).takeIf { it <= month }?.let { it to amountCents }
    }.maxByOrNull { it.first }?.second

data class InvestmentSummary(
    val investment: Investment,
    val investedCents: Long,
    val valueCents: Long,
    /** The part of the value that comes from the latest "update value", on top of invested + interest. */
    val valuationAdjustmentCents: Long = 0L,
    /** Interest/returns added in the overview's month. */
    val monthInterestCents: Long = 0L,
)

sealed interface InvestmentMovement {
    val id: String
    val amountCents: Long
    val notes: String
    val occurredOn: LocalDate
    val createdAtEpochMillis: Long

    /** Saldo <-> cuenta general. Positive = from Saldo into the pot. */
    data class Transfer(val transfer: InvestmentTransfer) : InvestmentMovement {
        override val id = transfer.id
        override val amountCents = transfer.amountCents
        override val notes = transfer.notes
        override val occurredOn = transfer.occurredOn
        override val createdAtEpochMillis = transfer.createdAtEpochMillis
    }

    /** Cuenta general <-> one investment. Positive = from the pot into the investment. */
    data class Allocation(val allocation: InvestmentAllocation, val investmentName: String) : InvestmentMovement {
        override val id = allocation.id
        override val amountCents = allocation.amountCents
        override val notes = allocation.notes
        override val occurredOn = allocation.occurredOn
        override val createdAtEpochMillis = allocation.createdAtEpochMillis
    }

    /** The value set for the pot or an investment; [amountCents] is the resulting value. */
    data class Valuation(
        val valuation: InvestmentValuation,
        val targetName: String?,
        override val amountCents: Long,
    ) : InvestmentMovement {
        override val id = valuation.id
        override val notes = ""
        override val occurredOn = valuation.occurredOn
        override val createdAtEpochMillis = valuation.createdAtEpochMillis
        val isPot get() = valuation.targetId == GENERAL_POT_ID
    }

    /** Interest/returns added to the pot or an investment. */
    data class Interest(val interest: InvestmentInterest, val targetName: String?) : InvestmentMovement {
        override val id = interest.id
        override val amountCents = interest.amountCents
        override val notes = ""
        override val occurredOn = interest.occurredOn
        override val createdAtEpochMillis = interest.createdAtEpochMillis
        val isPot get() = interest.targetId == GENERAL_POT_ID
    }
}

data class InvestmentOverview(
    val month: YearMonth,
    /** Net money moved from the Saldo into investments up to the end of [month]. */
    val contributedCents: Long,
    /** Cash sitting in the general pot (e.g. the remunerated account), interest included. */
    val potValueCents: Long,
    /** Everything the pot has earned so far (interest plus value updates). */
    val potGainCents: Long,
    val potValuationAdjustmentCents: Long,
    /** Interest added to the pot in [month]. */
    val potMonthInterestCents: Long,
    val investments: List<InvestmentSummary>,
    val totalValueCents: Long,
    val totalGainCents: Long,
    /** Movements, interest and valuations dated in [month]. */
    val movements: List<InvestmentMovement>,
) {
    companion object {
        fun empty() = InvestmentOverview(YearMonth.now(), 0L, 0L, 0L, 0L, 0L, emptyList(), 0L, 0L, emptyList())
    }
}

/** The state of every investment at the end of [month]. */
internal fun buildInvestmentOverview(
    transfers: List<InvestmentTransfer>,
    investments: List<Investment>,
    allocations: List<InvestmentAllocation>,
    valuations: List<InvestmentValuation>,
    month: YearMonth,
    interests: List<InvestmentInterest> = emptyList(),
): InvestmentOverview {
    val monthEnd = month.atEndOfMonth()
    fun inMonth(date: LocalDate) = YearMonth.from(date) == month
    fun investedIn(targetId: String, until: LocalDate): Long = if (targetId == GENERAL_POT_ID) {
        transfers.filter { it.occurredOn <= until }.sumOf { it.amountCents } -
            allocations.filter { it.occurredOn <= until }.sumOf { it.amountCents }
    } else {
        allocations.filter { it.investmentId == targetId && it.occurredOn <= until }.sumOf { it.amountCents }
    }
    fun interestOf(targetId: String, until: LocalDate): Long =
        interests.filter { it.targetId == targetId && it.occurredOn <= until }.sumOf { it.amountCents }
    fun monthInterestOf(targetId: String): Long =
        interests.filter { it.targetId == targetId && inMonth(it.occurredOn) }.sumOf { it.amountCents }
    val latestValuation = valuations
        .filter { it.occurredOn <= monthEnd }
        .groupBy { it.targetId }
        .mapValues { (_, items) -> items.maxWith(compareBy<InvestmentValuation> { it.occurredOn }.thenBy { it.createdAtEpochMillis }) }
    fun adjustmentOf(targetId: String): Long = latestValuation[targetId]?.gainCents ?: 0L
    fun valueOf(targetId: String): Long = investedIn(targetId, monthEnd) + interestOf(targetId, monthEnd) + adjustmentOf(targetId)

    val contributedCents = transfers.filter { it.occurredOn <= monthEnd }.sumOf { it.amountCents }
    val potValueCents = valueOf(GENERAL_POT_ID)
    val summaries = investments
        .sortedBy { it.createdAtEpochMillis }
        .filter { investment ->
            val hasActivityByMonthEnd = allocations.any { it.investmentId == investment.id && it.occurredOn <= monthEnd } ||
                investment.id in latestValuation ||
                interests.any { it.targetId == investment.id && it.occurredOn <= monthEnd }
            val hasNoActivityAtAll = allocations.none { it.investmentId == investment.id } &&
                valuations.none { it.targetId == investment.id } &&
                interests.none { it.targetId == investment.id }
            hasActivityByMonthEnd || hasNoActivityAtAll
        }
        .map { investment ->
            InvestmentSummary(
                investment = investment,
                investedCents = investedIn(investment.id, monthEnd),
                valueCents = valueOf(investment.id),
                valuationAdjustmentCents = adjustmentOf(investment.id),
                monthInterestCents = monthInterestOf(investment.id),
            )
        }
    val totalValueCents = potValueCents + summaries.sumOf { it.valueCents }
    val namesById = investments.associate { it.id to it.name }
    val movements = (
        transfers.filter { inMonth(it.occurredOn) }.map { InvestmentMovement.Transfer(it) } +
            allocations.filter { inMonth(it.occurredOn) }
                .map { InvestmentMovement.Allocation(it, namesById[it.investmentId].orEmpty()) } +
            interests.filter { inMonth(it.occurredOn) }.map { InvestmentMovement.Interest(it, namesById[it.targetId]) } +
            valuations.filter { inMonth(it.occurredOn) }.map { valuation ->
                InvestmentMovement.Valuation(
                    valuation = valuation,
                    targetName = namesById[valuation.targetId],
                    amountCents = investedIn(valuation.targetId, valuation.occurredOn) +
                        interestOf(valuation.targetId, valuation.occurredOn) + valuation.gainCents,
                )
            }
        ).sortedWith(
        compareByDescending<InvestmentMovement> { it.occurredOn }.thenByDescending { it.createdAtEpochMillis },
    )
    return InvestmentOverview(
        month = month,
        contributedCents = contributedCents,
        potValueCents = potValueCents,
        potGainCents = potValueCents - investedIn(GENERAL_POT_ID, monthEnd),
        potValuationAdjustmentCents = adjustmentOf(GENERAL_POT_ID),
        potMonthInterestCents = monthInterestOf(GENERAL_POT_ID),
        investments = summaries,
        totalValueCents = totalValueCents,
        totalGainCents = totalValueCents - contributedCents,
        movements = movements,
    )
}

/** Date used for actions done while looking at [month]: today in the current month, else its last day. */
internal fun actionDateFor(month: YearMonth, today: LocalDate = LocalDate.now()): LocalDate =
    if (month == YearMonth.from(today)) today else month.atEndOfMonth()

data class SumptusUiState(
    val selectedMonth: YearMonth,
    val monthLabel: String,
    val monthExpenseTotalCents: Long,
    val monthIncomeTotalCents: Long,
    val monthNetCents: Long,
    val currentBalanceCents: Long,
    val balanceAdjustmentCents: Long,
    /** Net money moved from the Saldo into investments in the selected month. */
    val monthInvestmentTransferCents: Long = 0L,
    val categories: List<String>,
    val categoryColors: Map<String, Long>,
    val budgetOverview: BudgetOverview,
    val generalExtraLimitCents: Long?,
    val lastThreeMonthExpenses: List<MonthExpenseComparison>,
    val recentExpenses: List<RecentExpense>,
    val historyEntries: List<TimelineEntry>,
    val investmentOverview: InvestmentOverview,
    val isDarkTheme: Boolean,
    val language: AppLanguage = AppLanguage.systemDefault(),
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
            generalExtraLimitCents = null,
            lastThreeMonthExpenses = emptyList(),
            recentExpenses = emptyList(),
            historyEntries = emptyList(),
            investmentOverview = InvestmentOverview.empty(),
            isDarkTheme = false,
            feedbackMessage = null,
        )
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExpenseRepository(application.applicationContext)
    private val language = repository.language.stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.systemDefault())
    private val strings: Strings get() = Strings(language.value)
    private val feedback = MutableStateFlow<String?>(null)
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private data class SummaryInputs(
        val expenses: List<ExpenseEntry>,
        val categories: List<String>,
        val categoryColors: Map<String, Long>,
        val extraEntries: List<ExtraEntry>,
        val budgetChanges: Map<String, Long>,
        val generalExtraLimitChanges: Map<String, Long>,
        val monthlyBalanceAdjustments: Map<String, Long>,
        val selectedMonth: YearMonth,
    )

    private data class InvestmentInputs(
        val transfers: List<InvestmentTransfer>,
        val investments: List<Investment>,
        val allocations: List<InvestmentAllocation>,
        val valuations: List<InvestmentValuation>,
        val interests: List<InvestmentInterest>,
    )

    private val investmentInputs = combine(
        repository.investmentTransfers,
        repository.investments,
        repository.investmentAllocations,
        repository.investmentValuations,
        repository.investmentInterests,
    ) { transfers, investments, allocations, valuations, interests ->
        InvestmentInputs(transfers, investments, allocations, valuations, interests)
    }

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
        repository.generalExtraLimitChanges,
        selectedMonth,
    ) { entries, monthlyBalanceAdjustments, generalExtraLimitChanges, month ->
        SummaryInputs(
            expenses = entries.expenses,
            categories = entries.categories,
            categoryColors = entries.categoryColors,
            extraEntries = entries.extraEntries,
            budgetChanges = entries.budgetChanges,
            generalExtraLimitChanges = generalExtraLimitChanges,
            monthlyBalanceAdjustments = monthlyBalanceAdjustments,
            selectedMonth = month,
        )
    }

    val uiState: StateFlow<SumptusUiState> = combine(
        summaryInputs,
        investmentInputs,
        feedback,
        repository.darkTheme,
        language,
    ) { inputs, investmentData, message, isDarkTheme, appLanguage ->
        buildUiState(
            language = appLanguage,
            investmentInputs = investmentData,
            expenses = inputs.expenses,
            storedCategories = inputs.categories,
            categoryColors = inputs.categoryColors,
            extraEntries = inputs.extraEntries,
            budgetChanges = inputs.budgetChanges,
            generalExtraLimitChanges = inputs.generalExtraLimitChanges,
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
            feedback.value = strings.chooseCategory
            return false
        }

        val amountCents = parseAmountToCents(amountInput)
        if (amountCents == null || amountCents <= 0) {
            feedback.value = strings.invalidAmount
            return false
        }

        val occurredOn = parseExpenseInputDate(occurredOnInput)
        if (occurredOn == null) {
            feedback.value = strings.invalidDate
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
            feedback.value = if (existingExpense == null) strings.expenseSaved else strings.expenseUpdated
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
            feedback.value = strings.invalidAmount
            return false
        }

        val occurredOn = parseExpenseInputDate(occurredOnInput)
        if (occurredOn == null) {
            feedback.value = strings.invalidDate
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
            feedback.value = when {
                type == IncomeType.SALARY && existingExtra == null -> strings.salarySaved
                type == IncomeType.SALARY -> strings.salaryUpdated
                existingExtra == null -> strings.extraSaved
                else -> strings.extraUpdated
            }
        }
        return true
    }

    fun setCurrentBalance(amountInput: String): Boolean {
        val targetBalanceCents = parseAmountToCents(amountInput)
        if (targetBalanceCents == null) {
            feedback.value = strings.invalidBalance
            return false
        }

        val state = uiState.value
        val calculatedWithoutAdjustment = state.currentBalanceCents - state.balanceAdjustmentCents
        viewModelScope.launch {
            repository.setMonthlyBalanceAdjustmentCents(
                state.selectedMonth,
                targetBalanceCents - calculatedWithoutAdjustment,
            )
            feedback.value = strings.balanceAdjusted
        }
        return true
    }

    fun addCategory(categoryName: String): Boolean {
        val normalizedCategory = normalizeCategoryName(categoryName)
        if (normalizedCategory.isBlank()) {
            feedback.value = strings.typeCategory
            return false
        }

        if (uiState.value.categories.any { it.equals(normalizedCategory, ignoreCase = true) }) {
            feedback.value = strings.categoryExists
            return false
        }

        viewModelScope.launch {
            repository.addCategory(normalizedCategory)
            feedback.value = strings.categoryAdded
        }
        return true
    }

    fun deleteCategory(categoryName: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryName)
            feedback.value = strings.categoryDeleted
        }
    }

    fun setCategoryColor(categoryName: String, colorArgb: Long) {
        viewModelScope.launch {
            repository.setCategoryColor(categoryName, colorArgb)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.setLanguage(language)
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
            feedback.value = strings.invalidBudget
            return false
        }

        val state = uiState.value
        viewModelScope.launch {
            repository.setCategoryBudget(state.selectedMonth, category, amountCents)
            feedback.value = strings.budgetSaved
        }
        return true
    }

    fun setGeneralExtraLimit(amountInput: String): Boolean {
        val amountCents = if (amountInput.isBlank()) null else parseAmountToCents(amountInput)
        if (amountInput.isNotBlank() && (amountCents == null || amountCents < 0L)) {
            feedback.value = strings.invalidLimit
            return false
        }

        viewModelScope.launch {
            repository.setGeneralExtraLimitCents(uiState.value.selectedMonth, amountCents)
            feedback.value = strings.limitSaved
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
                feedback.value = strings.dataExported
            }.onFailure {
                feedback.value = strings.exportFailed
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
                feedback.value = if (imported) strings.dataImported else strings.invalidBackup
            }.onFailure {
                feedback.value = strings.importFailed
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            feedback.value = strings.expenseDeleted
        }
    }

    fun deleteIncome(income: ExtraEntry) {
        viewModelScope.launch {
            repository.deleteExtraEntry(income.id)
            feedback.value = if (income.type == IncomeType.SALARY) strings.salaryDeleted else strings.extraDeleted
        }
    }

    /** Moves money between the Saldo and the general investment pot. */
    fun saveInvestmentTransfer(amountInput: String, notes: String, occurredOnInput: String, toInvestments: Boolean): Boolean {
        val amountCents = parsePositiveAmount(amountInput) ?: return false
        val occurredOn = parseExpenseInputDate(occurredOnInput)
        if (occurredOn == null) {
            feedback.value = strings.invalidDate
            return false
        }
        if (!toInvestments && amountCents > uiState.value.investmentOverview.potValueCents) {
            feedback.value = strings.notEnoughInPot
            return false
        }

        viewModelScope.launch {
            repository.upsertInvestmentTransfer(
                InvestmentTransfer(
                    amountCents = if (toInvestments) amountCents else -amountCents,
                    notes = notes.trim(),
                    occurredOn = occurredOn,
                ),
            )
            feedback.value = if (toInvestments) strings.movedToInvestments else strings.returnedToBalance
        }
        return true
    }

    /** Sets the current value of the general pot (e.g. after the monthly interest is paid). */
    fun setGeneralPotValue(amountInput: String): Boolean {
        val targetCents = parseAmountToCents(amountInput)
        if (targetCents == null || targetCents < 0L) {
            feedback.value = strings.invalidAmount
            return false
        }
        val overview = uiState.value.investmentOverview
        val potWithoutAdjustment = overview.potValueCents - overview.potValuationAdjustmentCents
        viewModelScope.launch {
            repository.upsertInvestmentValuation(
                InvestmentValuation(
                    targetId = GENERAL_POT_ID,
                    occurredOn = actionDateFor(overview.month),
                    gainCents = targetCents - potWithoutAdjustment,
                ),
            )
            feedback.value = strings.valueUpdated
        }
        return true
    }

    fun addInvestment(name: String, initialAmountInput: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            feedback.value = strings.typeName
            return false
        }
        if (uiState.value.investmentOverview.investments.any { it.investment.name.equals(trimmedName, ignoreCase = true) }) {
            feedback.value = strings.investmentExists
            return false
        }
        val initialCents = if (initialAmountInput.isBlank()) 0L else parsePositiveAmount(initialAmountInput) ?: return false
        if (initialCents > uiState.value.investmentOverview.potValueCents) {
            feedback.value = strings.notEnoughInPot
            return false
        }

        val investment = Investment(name = trimmedName)
        viewModelScope.launch {
            repository.addInvestment(investment)
            if (initialCents > 0L) {
                repository.upsertInvestmentAllocation(
                    InvestmentAllocation(
                        investmentId = investment.id,
                        amountCents = initialCents,
                        occurredOn = actionDateFor(uiState.value.investmentOverview.month),
                    ),
                )
            }
            feedback.value = strings.investmentAdded
        }
        return true
    }

    /** Moves money between the general pot and one investment. */
    fun saveInvestmentAllocation(investmentId: String, amountInput: String, intoInvestment: Boolean): Boolean {
        val amountCents = parsePositiveAmount(amountInput) ?: return false
        val overview = uiState.value.investmentOverview
        val available = if (intoInvestment) {
            overview.potValueCents
        } else {
            overview.investments.firstOrNull { it.investment.id == investmentId }?.valueCents ?: 0L
        }
        if (amountCents > available) {
            feedback.value = if (intoInvestment) strings.notEnoughInPot else strings.investmentWorthLess
            return false
        }

        viewModelScope.launch {
            repository.upsertInvestmentAllocation(
                InvestmentAllocation(
                    investmentId = investmentId,
                    amountCents = if (intoInvestment) amountCents else -amountCents,
                    occurredOn = actionDateFor(overview.month),
                ),
            )
            feedback.value = if (intoInvestment) strings.moneyInvested else strings.returnedToPot
        }
        return true
    }

    fun setInvestmentValue(investmentId: String, amountInput: String): Boolean {
        val targetCents = parseAmountToCents(amountInput)
        if (targetCents == null || targetCents < 0L) {
            feedback.value = strings.invalidAmount
            return false
        }
        val summary = uiState.value.investmentOverview.investments.firstOrNull { it.investment.id == investmentId } ?: return false
        val gainCents = targetCents - (summary.valueCents - summary.valuationAdjustmentCents)
        val occurredOn = actionDateFor(uiState.value.investmentOverview.month)
        viewModelScope.launch {
            repository.upsertInvestmentValuation(
                InvestmentValuation(targetId = investmentId, occurredOn = occurredOn, gainCents = gainCents),
            )
            feedback.value = strings.valueUpdated
        }
        return true
    }

    /**
     * Adds interest/returns to the pot ([GENERAL_POT_ID]) or an investment, dated in the selected
     * month. Negative amounts (losses) are only allowed for investments.
     */
    fun addInvestmentInterest(targetId: String, amountInput: String): Boolean {
        val amountCents = parseAmountToCents(amountInput)
        if (amountCents == null || amountCents == 0L || (targetId == GENERAL_POT_ID && amountCents < 0L)) {
            feedback.value = strings.invalidAmount
            return false
        }
        val occurredOn = actionDateFor(uiState.value.investmentOverview.month)
        viewModelScope.launch {
            repository.addInvestmentInterest(
                InvestmentInterest(targetId = targetId, amountCents = amountCents, occurredOn = occurredOn),
            )
            feedback.value = strings.interestAdded
        }
        return true
    }

    fun deleteInvestment(investmentId: String) {
        viewModelScope.launch {
            repository.deleteInvestment(investmentId)
            feedback.value = strings.investmentDeleted
        }
    }

    fun deleteInvestmentMovement(movement: InvestmentMovement) {
        viewModelScope.launch {
            when (movement) {
                is InvestmentMovement.Transfer -> repository.deleteInvestmentTransfer(movement.id)
                is InvestmentMovement.Allocation -> repository.deleteInvestmentAllocation(movement.id)
                is InvestmentMovement.Valuation -> repository.deleteInvestmentValuation(movement.id)
                is InvestmentMovement.Interest -> repository.deleteInvestmentInterest(movement.id)
            }
            feedback.value = strings.movementDeleted
        }
    }

    private fun parsePositiveAmount(amountInput: String): Long? {
        val amountCents = parseAmountToCents(amountInput)
        if (amountCents == null || amountCents <= 0L) {
            feedback.value = strings.invalidAmount
            return null
        }
        return amountCents
    }

    fun clearFeedback() {
        feedback.value = null
    }

    private fun buildUiState(
        language: AppLanguage,
        investmentInputs: InvestmentInputs,
        expenses: List<ExpenseEntry>,
        storedCategories: List<String>,
        categoryColors: Map<String, Long>,
        extraEntries: List<ExtraEntry>,
        budgetChanges: Map<String, Long>,
        generalExtraLimitChanges: Map<String, Long>,
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
        val transfers = investmentInputs.transfers
        val currentBalanceCents = buildMonthEndingBalances(
            expenses,
            extraEntries,
            monthlyBalanceAdjustments,
            selectedMonth,
            transfers,
        )[selectedMonth] ?: 0L

        return SumptusUiState(
            selectedMonth = selectedMonth,
            monthLabel = formatMonth(selectedMonth, language.locale),
            monthExpenseTotalCents = monthExpenseTotal,
            monthIncomeTotalCents = monthIncomeTotal,
            monthNetCents = monthIncomeTotal - monthExpenseTotal,
            currentBalanceCents = currentBalanceCents,
            balanceAdjustmentCents = balanceAdjustmentCents,
            monthInvestmentTransferCents = transfers
                .filter { YearMonth.from(it.occurredOn) == selectedMonth }
                .sumOf { it.amountCents },
            categories = storedCategories,
            categoryColors = categoryColors,
            budgetOverview = buildBudgetOverview(
                expenses,
                storedCategories,
                budgetChanges,
                generalExtraLimitChanges,
                selectedMonth,
            ),
            generalExtraLimitCents = effectiveGeneralExtraLimit(generalExtraLimitChanges, selectedMonth),
            lastThreeMonthExpenses = buildLastThreeMonthExpenses(expenses, selectedMonth),
            recentExpenses = buildRecentExpenses(expenses, extraEntries, monthlyBalanceAdjustments, transfers),
            historyEntries = buildHistoryEntries(monthExpenses, monthExtras),
            investmentOverview = buildInvestmentOverview(
                transfers,
                investmentInputs.investments,
                investmentInputs.allocations,
                investmentInputs.valuations,
                selectedMonth,
                investmentInputs.interests,
            ),
            isDarkTheme = isDarkTheme,
            language = language,
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

    private fun formatMonth(month: YearMonth, locale: Locale): String {
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
