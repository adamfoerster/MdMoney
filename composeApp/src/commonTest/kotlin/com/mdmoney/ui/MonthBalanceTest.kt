package com.mdmoney.ui

import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class MonthBalanceTest {

    private fun note(
        type: ExpenseType,
        amounts: Map<Month, Double>,
        paid: Set<Month>,
        year: Int = 2026,
    ) = Expense.empty(account = "Regions", year = year, type = type).copy(
        id = "x",
        title = "x",
        amounts = Month.ALL.associateWith { amounts[it] },
        paid = Month.ALL.associateWith { it in paid },
    )

    // Opening 1000. Salary 3000 received Jan and Feb; rent 1200 paid Jan, Feb (unpaid in Mar).
    private val salary = note(ExpenseType.INCOME, mapOf(Month.JAN to 3000.0, Month.FEB to 3000.0), setOf(Month.JAN, Month.FEB))
    private val rent = note(
        ExpenseType.RECURRING_FIXED,
        mapOf(Month.JAN to 1200.0, Month.FEB to 1200.0, Month.MAR to 1200.0),
        setOf(Month.JAN, Month.FEB),
    )
    private val all = listOf(salary, rent)

    @Test
    fun january_starts_at_the_opening_balance_and_ends_after_its_own_movement() {
        val b = monthBalance(all, 2026, Month.JAN, 1000.0)
        assertEquals(1000.0, b.start)
        assertEquals(2800.0, b.end) // 1000 + 3000 - 1200
    }

    @Test
    fun a_month_starts_where_the_previous_one_ended() {
        val b = monthBalance(all, 2026, Month.FEB, 1000.0)
        assertEquals(2800.0, b.start)
        assertEquals(4600.0, b.end) // 2800 + 3000 - 1200
    }

    @Test
    fun an_unpaid_bill_moves_neither_figure() {
        val b = monthBalance(all, 2026, Month.MAR, 1000.0)
        assertEquals(4600.0, b.start)
        assertEquals(4600.0, b.end)
    }

    @Test
    fun december_end_is_the_account_balance() {
        // Same as opening + received total - paid total: 1000 + 6000 - 2400.
        assertEquals(4600.0, monthBalance(all, 2026, Month.DEC, 1000.0).end)
    }

    @Test
    fun other_years_do_not_count() {
        val old = note(ExpenseType.INCOME, mapOf(Month.JAN to 999.0), setOf(Month.JAN), year = 2025)
        assertEquals(1000.0, monthBalance(listOf(old), 2026, Month.JUN, 1000.0).start)
    }
}
