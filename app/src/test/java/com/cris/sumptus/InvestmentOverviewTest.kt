package com.cris.sumptus

import com.cris.sumptus.data.ExpenseEntry
import com.cris.sumptus.data.GENERAL_POT_ID
import com.cris.sumptus.data.Investment
import com.cris.sumptus.data.InvestmentAllocation
import com.cris.sumptus.data.InvestmentInterest
import com.cris.sumptus.data.InvestmentTransfer
import com.cris.sumptus.data.InvestmentValuation
import com.cris.sumptus.data.legacyGainsToValuations
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentOverviewTest {
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)

    @Test
    fun potHoldsTransfersMinusAllocationsPlusInterest() {
        val etf = Investment(id = "etf", name = "ETF")
        val overview = buildInvestmentOverview(
            transfers = listOf(
                InvestmentTransfer(amountCents = 100_000L, occurredOn = nov.atDay(1)),
                InvestmentTransfer(amountCents = -10_000L, occurredOn = nov.atDay(5)),
            ),
            investments = listOf(etf),
            allocations = listOf(InvestmentAllocation(investmentId = "etf", amountCents = 30_000L, occurredOn = nov.atDay(2))),
            valuations = listOf(
                InvestmentValuation(targetId = "etf", occurredOn = nov.atDay(6), gainCents = 5_000L),
                InvestmentValuation(targetId = GENERAL_POT_ID, occurredOn = nov.atDay(6), gainCents = 250L),
            ),
            month = nov,
        )

        assertEquals(90_000L, overview.contributedCents)
        assertEquals(90_000L - 30_000L + 250L, overview.potValueCents)
        assertEquals(35_000L, overview.investments.single().valueCents)
        assertEquals(5_250L, overview.totalGainCents)
        assertEquals(5, overview.movements.size)
    }

    @Test
    fun aSeptemberValueStaysInSeptemberAndCarriesUntilANewerOne() {
        val transfers = listOf(InvestmentTransfer(amountCents = 10_000L, occurredOn = sep.atDay(1)))
        val valuations = listOf(
            InvestmentValuation(targetId = GENERAL_POT_ID, occurredOn = sep.atEndOfMonth(), gainCents = 30L, createdAtEpochMillis = 2L),
            InvestmentValuation(targetId = GENERAL_POT_ID, occurredOn = nov.atDay(3), gainCents = 90L, createdAtEpochMillis = 1L),
        )
        fun potIn(month: YearMonth) = buildInvestmentOverview(transfers, emptyList(), emptyList(), valuations, month).potValueCents

        assertEquals(10_030L, potIn(sep))
        assertEquals(10_030L, potIn(oct))
        assertEquals(10_090L, potIn(nov))
        // Before the money was moved there is nothing.
        assertEquals(0L, potIn(sep.minusMonths(1)))
    }

    @Test
    fun anOldDatedTransferLowersThatMonthAndEveryLaterOne() {
        val expense = ExpenseEntry(id = "e", title = "", amountCents = 100L, category = "", notes = "", occurredOn = nov.atDay(2))
        val adjustments = mapOf(sep.toString() to 5_000L, oct.toString() to 0L, nov.toString() to 0L)
        val transfer = InvestmentTransfer(amountCents = 1_000L, occurredOn = sep.atDay(15))

        val balances = buildMonthEndingBalances(listOf(expense), emptyList(), adjustments, nov, listOf(transfer))

        assertEquals(4_000L, balances[sep])
        assertEquals(4_000L, balances[oct])
        assertEquals(3_900L, balances[nov])
    }

    @Test
    fun movementsListOnlyShowsTheSelectedMonth() {
        val transfers = listOf(
            InvestmentTransfer(amountCents = 1_000L, occurredOn = sep.atDay(1)),
            InvestmentTransfer(amountCents = 2_000L, occurredOn = nov.atDay(1)),
        )
        val overview = buildInvestmentOverview(transfers, emptyList(), emptyList(), emptyList(), sep)
        assertEquals(1, overview.movements.size)
        assertEquals(1_000L, overview.movements.single().amountCents)
    }

    @Test
    fun actionsWhileViewingAPastMonthAreDatedAtItsEnd() {
        val today = LocalDate.of(2026, 11, 3)
        assertEquals(LocalDate.of(2026, 9, 30), actionDateFor(sep, today))
        assertEquals(today, actionDateFor(nov, today))
    }

    @Test
    fun undatedGainsBecomeValuations() {
        val valuations = legacyGainsToValuations(
            listOf(Investment(id = "a", name = "A", gainCents = 70L), Investment(id = "b", name = "B")),
            potGainCents = 20L,
            date = nov.atDay(3),
        )
        assertEquals(2, valuations.size)
        assertTrue(valuations.any { it.targetId == GENERAL_POT_ID && it.gainCents == 20L })
        assertTrue(valuations.any { it.targetId == "a" && it.gainCents == 70L })
    }

    @Test
    fun interestAddsUpAndCarriesIntoLaterMonths() {
        val transfers = listOf(InvestmentTransfer(amountCents = 10_000L, occurredOn = sep.atDay(1)))
        val interests = listOf(
            InvestmentInterest(targetId = GENERAL_POT_ID, amountCents = 150L, occurredOn = sep.atEndOfMonth()),
            InvestmentInterest(targetId = GENERAL_POT_ID, amountCents = 160L, occurredOn = oct.atEndOfMonth()),
        )
        fun overviewIn(month: YearMonth) = buildInvestmentOverview(transfers, emptyList(), emptyList(), emptyList(), month, interests)

        assertEquals(10_150L, overviewIn(sep).potValueCents)
        assertEquals(10_310L, overviewIn(oct).potValueCents)
        assertEquals(160L, overviewIn(oct).potMonthInterestCents)
        assertEquals(310L, overviewIn(nov).potGainCents)
        assertEquals(10_310L, overviewIn(nov).potValueCents)
    }

    @Test
    fun interestAfterSettingTheTotalAddsOnTop() {
        val transfers = listOf(InvestmentTransfer(amountCents = 10_000L, occurredOn = sep.atDay(1)))
        val interests = listOf(InvestmentInterest(targetId = GENERAL_POT_ID, amountCents = 100L, occurredOn = sep.atDay(10)))
        // Total set to 10_500 in September while 100 of interest was already recorded: the
        // valuation only stores the remaining 400.
        val valuations = listOf(InvestmentValuation(targetId = GENERAL_POT_ID, occurredOn = sep.atDay(20), gainCents = 400L))
        val octInterest = interests + InvestmentInterest(targetId = GENERAL_POT_ID, amountCents = 50L, occurredOn = oct.atDay(5))

        assertEquals(10_500L, buildInvestmentOverview(transfers, emptyList(), emptyList(), valuations, sep, interests).potValueCents)
        assertEquals(10_550L, buildInvestmentOverview(transfers, emptyList(), emptyList(), valuations, oct, octInterest).potValueCents)
    }

    @Test
    fun transfersShowUpInLatestActivityWithTheBalanceAfterThem() {
        val expense = ExpenseEntry(
            id = "e", title = "", amountCents = 100L, category = "", notes = "",
            occurredOn = nov.atDay(2), createdAtEpochMillis = 1L,
        )
        val transfer = InvestmentTransfer(id = "t", amountCents = 400L, occurredOn = nov.atDay(3), createdAtEpochMillis = 2L)

        val recent = buildRecentExpenses(listOf(expense), emptyList(), mapOf(nov.toString() to 1_000L), listOf(transfer))

        assertEquals(listOf("t", "e"), recent.map { it.id })
        assertEquals(500L, recent[0].balanceAfterCents)
        assertEquals(900L, recent[1].balanceAfterCents)
    }

    @Test
    fun incomesShowUpInLatestActivity() {
        val income = com.cris.sumptus.data.ExtraEntry(
            id = "i", amountCents = 2_000L, notes = "", occurredOn = nov.atDay(1),
            type = com.cris.sumptus.data.IncomeType.SALARY, createdAtEpochMillis = 3L,
        )
        val recent = buildRecentExpenses(emptyList(), listOf(income), mapOf(nov.toString() to 1_000L))

        assertEquals("i", recent.single().id)
        assertEquals(3_000L, recent.single().balanceAfterCents)
    }

    @Test
    fun latestActivityFollowsTheDateGivenNotTheOrderAdded() {
        val older = ExpenseEntry(
            id = "older", title = "", amountCents = 100L, category = "", notes = "",
            occurredOn = nov.atDay(1), createdAtEpochMillis = 50L,
        )
        val newer = older.copy(id = "newer", amountCents = 200L, occurredOn = nov.atDay(5), createdAtEpochMillis = 10L)

        val recent = buildRecentExpenses(listOf(older, newer), emptyList(), mapOf(nov.toString() to 1_000L))

        assertEquals(listOf("newer", "older"), recent.map { it.id })
        assertEquals(700L, recent[0].balanceAfterCents)
        assertEquals(900L, recent[1].balanceAfterCents)
    }
}
