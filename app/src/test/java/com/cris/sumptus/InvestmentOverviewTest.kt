package com.cris.sumptus

import com.cris.sumptus.data.ExpenseEntry
import com.cris.sumptus.data.Investment
import com.cris.sumptus.data.InvestmentAllocation
import com.cris.sumptus.data.InvestmentTransfer
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentOverviewTest {
    private val month = YearMonth.of(2026, 10)

    @Test
    fun potHoldsTransfersMinusAllocationsPlusInterest() {
        val etf = Investment(id = "etf", name = "ETF", gainCents = 5_000L)
        val overview = buildInvestmentOverview(
            transfers = listOf(
                InvestmentTransfer(amountCents = 100_000L, occurredOn = month.atDay(1)),
                InvestmentTransfer(amountCents = -10_000L, occurredOn = month.atDay(5)),
            ),
            investments = listOf(etf),
            allocations = listOf(InvestmentAllocation(investmentId = "etf", amountCents = 30_000L, occurredOn = month.atDay(2))),
            potGainCents = 250L,
        )

        assertEquals(90_000L, overview.contributedCents)
        assertEquals(90_000L - 30_000L + 250L, overview.potValueCents)
        assertEquals(30_000L, overview.investments.single().investedCents)
        assertEquals(35_000L, overview.investments.single().valueCents)
        assertEquals(60_250L + 35_000L, overview.totalValueCents)
        assertEquals(5_250L, overview.totalGainCents)
        assertEquals(3, overview.movements.size)
    }

    @Test
    fun transfersLowerTheSaldoAndCarryOverWithoutCountingAsExpenses() {
        val expense = ExpenseEntry(
            id = "e",
            title = "",
            amountCents = 100L,
            category = "",
            notes = "",
            occurredOn = month.atDay(3),
            createdAtEpochMillis = 2L,
        )
        val transfer = InvestmentTransfer(id = "t", amountCents = 400L, occurredOn = month.atDay(2), createdAtEpochMillis = 1L)
        val adjustments = mapOf(month.toString() to 1_000L)

        val balances = buildMonthEndingBalances(listOf(expense), emptyList(), adjustments, month.plusMonths(1), listOf(transfer))
        assertEquals(500L, balances[month])
        assertEquals(500L, balances[month.plusMonths(1)])

        val recent = buildRecentExpenses(listOf(expense), emptyList(), adjustments, listOf(transfer))
        assertEquals(500L, recent.single().balanceAfterCents)
    }
}
