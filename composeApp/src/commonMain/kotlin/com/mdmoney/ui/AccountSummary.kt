package com.mdmoney.ui

import com.mdmoney.domain.Currency
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month

/** What the accounts list shows for one account: where its balance stands and what is still owed. */
data class AccountSummary(
    /** The account's display title; the folder name when its root note has none. */
    val title: String,
    val currency: Currency,
    /** Null until an opening balance has been set for the year. */
    val balance: Double?,
    /** The month [pending] was picked for. */
    val month: Month,
    /** Recurring bills of [month] not yet marked paid. */
    val pending: List<Expense>,
) {
    /** What the pending bills add up to — a bill still without an amount counts as nothing. */
    val pendingTotal: Double get() = pending.sumOf { it.amount(month) ?: 0.0 }

    /** Variable bills still waiting for this month's amount — not in [pendingTotal] yet. */
    val awaitingAmount: Int get() = pending.count { it.type == ExpenseType.RECURRING_VARIABLE && it.amount(month) == null }
}

/**
 * The recurring bills of [month]/[year] not yet marked paid, sorted by title. Income, one-offs and
 * ledgers are not "recurring spending" and never appear.
 */
fun pendingRecurring(expenses: List<Expense>, year: Int, month: Month): List<Expense> =
    expenses
        .filter {
            it.year == year && !it.ledger &&
                (it.type == ExpenseType.RECURRING_FIXED || it.type == ExpenseType.RECURRING_VARIABLE) &&
                !it.isPaid(month)
        }
        .sortedBy { it.title.lowercase() }
