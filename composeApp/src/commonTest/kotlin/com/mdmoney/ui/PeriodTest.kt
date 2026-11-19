package com.mdmoney.ui

import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.importer.SimpleDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeriodTest {

    private fun note(
        title: String,
        type: ExpenseType,
        amounts: Map<Month, Double>,
        paid: Set<Month>,
    ) = Expense.empty(account = "A", year = 2026, type = type).copy(
        id = title,
        title = title,
        amounts = Month.ALL.associateWith { amounts[it] },
        paid = Month.ALL.associateWith { it in paid },
    )

    private val salary = note("Salary", ExpenseType.INCOME, mapOf(Month.JAN to 3000.0, Month.FEB to 3000.0), setOf(Month.JAN, Month.FEB))
    private val rent = note(
        "Rent",
        ExpenseType.RECURRING_FIXED,
        mapOf(Month.JAN to 1200.0, Month.FEB to 1200.0, Month.MAR to 1200.0),
        setOf(Month.JAN, Month.FEB),
    )
    private val food = Ledger(
        id = "2026 Mar - Food", account = "A", title = "Food", category = null, year = 2026, month = Month.MAR,
        entries = listOf(
            LedgerEntry("20260305", "market", 50.0),
            LedgerEntry("20260320", "market", 70.0),
        ),
    )
    private val all = listOf(salary, rent, food.toExpense())
    private val loaded = mapOf(food.id to food)

    private fun day(m: Int, d: Int) = SimpleDate(2026, m, d)

    // --- Months list ---

    @Test
    fun months_list_sums_only_the_picked_months_and_counts_unpaid_bills() {
        val period = Period.Months(setOf(YearMonth(2026, 1), YearMonth(2026, 3)))
        val lines = periodLines(all, emptyMap(), period)
        assertEquals(listOf(PeriodLine("Salary", null, 3000.0)), lines.income)
        assertEquals(listOf(PeriodLine("Rent", null, 2400.0)), lines.recurring) // Jan 1200 + Mar 1200 (unpaid)
        assertEquals(listOf(PeriodLine("Food", null, 120.0)), lines.eventual)
    }

    @Test
    fun months_left_out_of_a_list_still_count_towards_the_balance() {
        val period = Period.Months(setOf(YearMonth(2026, 1), YearMonth(2026, 3)))
        val b = periodBalance(all, emptyMap(), period, initial = 1000.0)
        assertEquals(1000.0, b.start)
        assertEquals(4480.0, b.end) // 1000 + 6000 − 2400 − 120, February included
    }

    @Test
    fun a_period_holds_at_most_twelve_months() {
        val thirteen = (1..13).map { YearMonth(2026 + (it - 1) / 12, (it - 1) % 12 + 1) }.toSet()
        assertFailsWith<IllegalArgumentException> { Period.Months(thirteen) }
    }

    @Test
    fun toggling_adds_removes_and_refuses_a_thirteenth() {
        val twelve = (1..12).map { YearMonth(2026, it) }.toSet()
        assertEquals(twelve, twelve.toggled(YearMonth(2027, 1)))
        assertEquals(11, twelve.toggled(YearMonth(2026, 5)).size)
        assertEquals(setOf(YearMonth(2026, 5)), emptySet<YearMonth>().toggled(YearMonth(2026, 5)))
    }

    @Test
    fun months_may_span_years() {
        val period = Period.Months(setOf(YearMonth(2025, 12), YearMonth(2026, 2)))
        assertEquals(20251201, period.firstDay)
        assertEquals(20260228, period.lastDay)
    }

    // --- Day range ---

    @Test
    fun a_range_filters_a_ledgers_purchases_by_day() {
        val period = Period.Range(day(3, 10), day(3, 31))
        val lines = periodLines(all, loaded, period)
        assertEquals(listOf(PeriodLine("Food", null, 70.0)), lines.eventual) // only the 20th
        assertTrue(lines.income.isEmpty())
        assertTrue(lines.recurring.isEmpty()) // March's rent is dated the 1st, before the range
    }

    @Test
    fun a_range_balance_splits_a_month_on_the_ledgers_own_days() {
        val b = periodBalance(all, loaded, Period.Range(day(3, 10), day(3, 31)), initial = 1000.0)
        assertEquals(4550.0, b.start) // 1000 + 3600 (Jan–Feb) − 50 (the 5th); March rent unpaid
        assertEquals(4480.0, b.end) // − 70 (the 20th)
    }

    @Test
    fun a_ledger_not_loaded_lands_whole_on_the_first_of_its_month() {
        val b = periodBalance(all, emptyMap(), Period.Range(day(3, 10), day(3, 31)), initial = 1000.0)
        assertEquals(4480.0, b.start) // all 120 already out by the 10th
        assertEquals(4480.0, b.end)
    }

    @Test
    fun a_plain_notes_month_counts_when_the_range_reaches_its_first() {
        val fromFeb2 = periodLines(all, loaded, Period.Range(day(2, 2), day(2, 28)))
        assertTrue(fromFeb2.income.isEmpty()) // February's salary is dated the 1st
        val fromFeb1 = periodLines(all, loaded, Period.Range(day(2, 1), day(2, 28)))
        assertEquals(3000.0, fromFeb1.income.single().amount)
    }

    @Test
    fun a_range_cannot_end_before_it_starts() {
        assertFailsWith<IllegalArgumentException> { Period.Range(day(3, 2), day(3, 1)) }
    }

    // --- Picking a range ---

    @Test
    fun two_taps_make_a_range_and_an_earlier_second_tap_swaps_them() {
        val first = RangeDraft().tap(day(3, 10))
        assertEquals(Period.Range(day(3, 10), day(3, 10)), first.toPeriod())
        assertEquals(Period.Range(day(3, 10), day(3, 20)), first.tap(day(3, 20)).toPeriod())
        assertEquals(Period.Range(day(3, 5), day(3, 10)), first.tap(day(3, 5)).toPeriod())
    }

    @Test
    fun a_third_tap_starts_over() {
        val done = RangeDraft().tap(day(3, 10)).tap(day(3, 20))
        assertEquals(RangeDraft(start = day(4, 1)), done.tap(day(4, 1)))
        assertNull(RangeDraft().toPeriod())
    }

    // --- Calendar ---

    @Test
    fun weekdays_match_known_dates() {
        assertEquals(4, dayOfWeek(2026, 11, 19)) // Thursday
        assertEquals(2, dayOfWeek(2026, 9, 1)) // Tuesday
        assertEquals(6, dayOfWeek(2000, 1, 1)) // Saturday
        assertEquals(4, dayOfWeek(2024, 2, 29)) // Thursday, a leap day
    }

    @Test
    fun september_2026_starts_on_a_tuesday_and_has_five_weeks() {
        val grid = monthGrid(2026, 9)
        assertEquals(5, grid.size)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(listOf(null, null, SimpleDate(2026, 9, 1)), grid[0].take(3))
        assertEquals(30, grid.flatten().filterNotNull().size)
        assertEquals(SimpleDate(2026, 9, 30), grid.flatten().filterNotNull().last())
    }
}
