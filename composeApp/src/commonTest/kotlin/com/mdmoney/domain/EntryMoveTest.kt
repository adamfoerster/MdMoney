package com.mdmoney.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryMoveTest {

    @Test
    fun replace_uses_the_entry_and_add_sums_to_the_cent() {
        assertEquals(62.72, mergedAmount(55.0, 62.72, MergeMode.REPLACE))
        assertEquals(117.72, mergedAmount(55.0, 62.72, MergeMode.ADD))
        // 0.1 + 0.2 must not leave float dust in a file.
        assertEquals(0.3, mergedAmount(0.1, 0.2, MergeMode.ADD))
        assertEquals(10.0, mergedAmount(null, 10.0, MergeMode.ADD), "nothing to add to")
    }

    @Test
    fun only_a_real_value_needs_the_question() {
        assertTrue(needsMergeChoice(55.0))
        assertFalse(needsMergeChoice(null))
        assertFalse(needsMergeChoice(0.0))
    }

    private fun ledger(title: String, month: Month = Month.OCT, income: Boolean = false, year: Int = 2026) =
        Ledger("$year ${month.englishShort} - $title", "a", title, null, year, month, listOf(LedgerEntry("${year}1001", "x", 1.0)), income)

    @Test
    fun destinations_are_the_other_groups_of_that_month_and_kind() {
        val electricity = ledger("Electricity")
        val rows = listOf(
            electricity.toExpense(),
            ledger("house").toExpense(),
            ledger("Car").toExpense(),
            ledger("Car", month = Month.SEP).toExpense(),
            ledger("Refund", income = true).toExpense(),
            ledger("Old", year = 2025).toExpense(),
            Expense.empty("a", 2026, ExpenseType.EVENTUAL).copy(id = "Plain", title = "Plain"),
        )

        assertEquals(listOf("Car", "house"), oneOffDestinations(rows, electricity).map { it.title })
    }

    @Test
    fun recurring_destinations_are_this_years_recurring_notes() {
        fun note(title: String, type: ExpenseType, year: Int = 2026) =
            Expense.empty("a", year, type).copy(id = title, title = title)
        val rows = listOf(
            note("Luz", ExpenseType.RECURRING_VARIABLE),
            note("condo", ExpenseType.RECURRING_FIXED),
            note("Tax", ExpenseType.EVENTUAL),
            note("Salary", ExpenseType.INCOME),
            note("Old", ExpenseType.RECURRING_FIXED, year = 2025),
            ledger("Group").toExpense(),
        )

        assertEquals(listOf("condo", "Luz"), recurringDestinations(rows, 2026).map { it.title })
    }
}
