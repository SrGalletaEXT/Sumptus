package com.cris.sumptus.data

import com.cris.sumptus.buildMonthEndingBalances
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class AdjustmentCarryoverMigrationTest {
    private val aug = YearMonth.of(2026, 8)
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)

    private fun expense(id: String, month: YearMonth, cents: Long) =
        ExpenseEntry(id = id, title = "", amountCents = cents, category = "", notes = "", occurredOn = month.atDay(10))

    @Test
    fun keepsTheBalancesTheUserSetAndCarriesOverMonthsWithoutAdjustment() {
        val expenses = listOf(expense("a", aug, 1_000L), expense("s", sep, 2_000L), expense("o", oct, 500L), expense("n", nov, 300L))
        // Old model: each month started at 0, so every adjustment held the full opening balance.
        val oldAdjustments = mapOf(aug.toString() to 15_000L, sep.toString() to 14_500L, oct.toString() to 15_000L)
        // What the user saw before: Aug 14_000, Sep 12_500, Oct 14_500.

        val converted = convertIndependentMonthAdjustmentsToCarryover(oldAdjustments, expenses, emptyList())
        val balances = buildMonthEndingBalances(expenses, emptyList(), converted, nov)

        assertEquals(14_000L, balances[aug])
        assertEquals(12_500L, balances[sep])
        assertEquals(14_500L, balances[oct])
        // November has no adjustment: it starts from October's balance instead of 0.
        assertEquals(14_200L, balances[nov])
    }

    @Test
    fun countsActivityBeforeTheFirstAdjustment() {
        val expenses = listOf(expense("a", aug, 1_000L), expense("o", oct, 500L))
        val converted = convertIndependentMonthAdjustmentsToCarryover(mapOf(oct.toString() to 9_000L), expenses, emptyList())
        val balances = buildMonthEndingBalances(expenses, emptyList(), converted, oct)

        assertEquals(8_500L, balances[oct])
    }
}
