package com.mdmoney.ui

import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnnualSummaryTest {
    private fun line(title: String, type: ExpenseType, amounts: Map<Month, Double>, year: Int = 2026) =
        Expense.empty(account = "Regions", year = year, type = type)
            .copy(id = title, title = title, amounts = Month.ALL.associateWith { amounts[it] })

    private val all = listOf(
        line("Received", ExpenseType.INCOME, mapOf(Month.JUL to 6745.82)),
        line("Condo", ExpenseType.RECURRING_FIXED, mapOf(Month.JUL to 212.0, Month.AUG to 212.0)),
        line("Luz", ExpenseType.RECURRING_VARIABLE, mapOf(Month.JUL to 55.0, Month.AUG to 0.0)),
        line("Old", ExpenseType.EVENTUAL, mapOf(Month.JUL to 999.0), year = 2025),
    )

    @Test
    fun july_totals_and_net_are_hand_computed() {
        val d = monthDetail(all, 2026, Month.JUL)

        assertEquals(6745.82, d.incomeTotal, 0.001)
        assertEquals(267.0, d.expenseTotal, 0.001) // 212 + 55, not the 2025 line
        assertEquals(6478.82, d.net, 0.001)
        assertEquals(listOf("Condo", "Luz"), d.expenses.map { it.title }, "largest first")
    }

    @Test
    fun zero_and_empty_amounts_are_not_lines() {
        val aug = monthDetail(all, 2026, Month.AUG)

        assertEquals(listOf("Condo"), aug.expenses.map { it.title }, "Luz's 0.00 is dropped")
        assertEquals(212.0, monthSpent(all, 2026, Month.AUG)!!, 0.001)
    }

    @Test
    fun a_month_with_no_spending_is_null_not_zero() {
        assertNull(monthSpent(all, 2026, Month.JAN))
        assertNull(monthSpent(listOf(all[0]), 2026, Month.JUL), "income alone is not spending")
    }

    @Test
    fun year_view_totals_each_line_across_its_months() {
        val d = monthDetail(all, 2026, null)

        // Condo 212 + 212 = 424, Luz 55 + 0 = 55, income 6745.82; the 2025 line stays out.
        assertEquals(listOf("Condo" to 424.0, "Luz" to 55.0), d.expenses.map { it.title to it.valueIn(null) })
        assertEquals(479.0, d.expenseTotal, 0.001)
        assertEquals(6266.82, d.net, 0.001)
        assertEquals(479.0, monthSpent(all, 2026, null)!!, 0.001)
    }
}
