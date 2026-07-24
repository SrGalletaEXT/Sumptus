package com.cris.sumptus

import com.cris.sumptus.data.ExpenseEntry
import com.cris.sumptus.data.ExtraEntry
import com.cris.sumptus.data.categoryBudgetKey
import com.cris.sumptus.data.categoryColorKey
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentExpenseBalanceTest {
    @Test
    fun keepsTheSameColorKeyForEquivalentCategoryNames() {
        assertEquals("viajes", categoryColorKey(" Viajes "))
        assertEquals("viajes", categoryColorKey("VIAJES"))
    }

    @Test
    fun showsTheBalanceAfterEachExpense() {
        val firstExpense = ExpenseEntry(
            id = "first",
            title = "",
            amountCents = 500L,
            category = "",
            notes = "",
            occurredOn = LocalDate.now(),
            createdAtEpochMillis = 1,
        )
        val secondExpense = firstExpense.copy(id = "second", amountCents = 200L, createdAtEpochMillis = 2)
        val income = ExtraEntry(
            id = "income",
            amountCents = 700L,
            notes = "",
            occurredOn = LocalDate.now(),
            createdAtEpochMillis = 3,
        )

        val balances = buildRecentExpenses(
            listOf(firstExpense, secondExpense),
            listOf(income),
            mapOf(YearMonth.now().toString() to 1_500L),
        )
            .associate { it.expense.id to it.balanceAfterCents }

        assertEquals(1_000L, balances["first"])
        assertEquals(800L, balances["second"])
    }

    @Test
    fun keepsBalancesIndependentForEachMonth() {
        val currentMonth = YearMonth.now()
        val previousMonth = currentMonth.minusMonths(1)
        val currentExpense = ExpenseEntry(
            id = "current",
            title = "",
            amountCents = 300L,
            category = "",
            notes = "",
            occurredOn = currentMonth.atDay(1),
            createdAtEpochMillis = 2,
        )
        val previousExpense = currentExpense.copy(
            id = "previous",
            amountCents = 100L,
            occurredOn = previousMonth.atDay(1),
            createdAtEpochMillis = 1,
        )

        val balances = buildRecentExpenses(
            listOf(currentExpense, previousExpense),
            emptyList(),
            mapOf(currentMonth.toString() to 1_000L, previousMonth.toString() to 2_000L),
        ).associate { it.expense.id to it.balanceAfterCents }

        assertEquals(700L, balances["current"])
        assertEquals(1_900L, balances["previous"])
    }

    @Test
    fun carriesUnspentBudgetIntoTheNextMonth() {
        val currentMonth = YearMonth.now()
        val previousMonth = currentMonth.minusMonths(1)
        val expense = ExpenseEntry(
            id = "food",
            title = "",
            amountCents = 3_500L,
            category = "Comida",
            notes = "",
            occurredOn = previousMonth.atDay(1),
        )
        val otherCategoryExpense = expense.copy(
            id = "transport",
            amountCents = 700L,
            category = "Transporte",
            occurredOn = currentMonth.atDay(1),
        )

        val budget = buildBudgetOverview(
            expenses = listOf(expense, otherCategoryExpense),
            categories = listOf("Comida"),
            budgetChanges = mapOf(categoryBudgetKey(previousMonth, "Comida") to 5_000L),
            generalExtraLimitChanges = mapOf(previousMonth.toString() to 1_000L),
            selectedMonth = currentMonth,
        )

        assertEquals(5_000L, budget.baseCents)
        assertEquals(1_000L, budget.carryoverCents)
        assertEquals(6_000L, budget.remainingCents)
    }

    @Test
    fun usesTheExtraLimitConfiguredForEachMonth() {
        val currentMonth = YearMonth.now()
        val previousMonth = currentMonth.minusMonths(1)
        val limits = mapOf(previousMonth.toString() to 1_000L, currentMonth.toString() to 2_000L)

        assertEquals(1_000L, effectiveGeneralExtraLimit(limits, previousMonth))
        assertEquals(2_000L, effectiveGeneralExtraLimit(limits, currentMonth))
    }
}
