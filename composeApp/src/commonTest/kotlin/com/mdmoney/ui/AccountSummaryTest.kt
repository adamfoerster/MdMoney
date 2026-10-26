package com.mdmoney.ui

import com.mdmoney.domain.Currency
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountSummaryTest {

    private fun note(
        title: String,
        type: ExpenseType,
        amounts: Map<Month, Double> = emptyMap(),
        paid: Set<Month> = emptySet(),
        year: Int = 2026,
        ledger: Boolean = false,
    ) = Expense.empty(account = "Regions", year = year, type = type).copy(
        id = title,
        title = title,
        amounts = Month.ALL.associateWith { amounts[it] },
        paid = Month.ALL.associateWith { it in paid },
        ledger = ledger,
    )

    @Test
    fun only_unpaid_recurring_bills_of_the_month_are_pending() {
        val rows = listOf(
            note("Aluguel", ExpenseType.RECURRING_FIXED, mapOf(Month.OCT to 1000.0)),
            note("Internet", ExpenseType.RECURRING_FIXED, mapOf(Month.OCT to 100.0), paid = setOf(Month.OCT)),
            note("Luz", ExpenseType.RECURRING_VARIABLE),
            note("Salário", ExpenseType.INCOME, mapOf(Month.OCT to 5000.0)),
            note("Seguro", ExpenseType.EVENTUAL, mapOf(Month.OCT to 300.0)),
            note("Antigo", ExpenseType.RECURRING_FIXED, mapOf(Month.OCT to 50.0), year = 2025),
        )

        assertEquals(listOf("Aluguel", "Luz"), pendingRecurring(rows, 2026, Month.OCT).map { it.title })
    }

    @Test
    fun paid_in_another_month_does_not_count_as_paid_this_month() {
        val rows = listOf(note("Aluguel", ExpenseType.RECURRING_FIXED, mapOf(Month.OCT to 1000.0), paid = setOf(Month.SEP)))

        assertEquals(1, pendingRecurring(rows, 2026, Month.OCT).size)
    }

    @Test
    fun pending_total_sums_amounts_and_ignores_bills_without_one() {
        val pending = listOf(
            note("Aluguel", ExpenseType.RECURRING_FIXED, mapOf(Month.OCT to 1000.5)),
            note("Condomínio", ExpenseType.RECURRING_FIXED, mapOf(Month.OCT to 300.25)),
            note("Luz", ExpenseType.RECURRING_VARIABLE),
        )
        val summary = AccountSummary("Regions", Currency.NONE, 10.0, Month.OCT, pending)

        assertEquals(1300.75, summary.pendingTotal)
    }

    @Test
    fun awaiting_amount_counts_only_variable_bills_without_one() {
        val pending = listOf(
            note("Aluguel", ExpenseType.RECURRING_FIXED),
            note("Luz", ExpenseType.RECURRING_VARIABLE),
            note("Água", ExpenseType.RECURRING_VARIABLE, mapOf(Month.OCT to 40.0)),
        )

        assertEquals(1, AccountSummary("Regions", Currency.NONE, null, Month.OCT, pending).awaitingAmount)
    }
}
