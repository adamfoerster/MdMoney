package com.mdmoney.domain

/**
 * One expense line item for a single account and year.
 *
 * Mirrors one markdown note in the vault: 12 monthly amounts plus a paid flag each, together with
 * classification metadata. [id] is the note's file name (without extension) and doubles as its
 * stable identity within an account.
 */
data class Expense(
    val id: String,
    val account: String,
    val title: String,
    val category: String?,
    val year: Int,
    val type: ExpenseType,
    val period: String?,
    val subStatus: String?,
    val projected: Double?,
    val tags: List<String>,
    val amounts: Map<Month, Double?>,
    val paid: Map<Month, Boolean>,
    /**
     * True when this row is the projection of a [Ledger] note (one-off spending grouped by month).
     * Its amount is derived from the ledger's rows, so it must be edited through the ledger rather
     * than the cell/expense editor — writing it back as a plain note would destroy the table.
     */
    val ledger: Boolean = false,
) {
    fun amount(month: Month): Double? = amounts[month]

    /** For income this reads as *received* — the same flag, seen from the other side. */
    fun isPaid(month: Month): Boolean = paid[month] ?: false

    /** Money coming in rather than out; income adds to the balance instead of subtracting. */
    val isIncome: Boolean get() = type == ExpenseType.INCOME

    /** Sum of all months that carry a value (nulls treated as zero). */
    val total: Double get() = Month.ALL.sumOf { amounts[it] ?: 0.0 }

    companion object {
        /** A blank expense used as the starting point when adding a new item. */
        fun empty(account: String, year: Int, type: ExpenseType = ExpenseType.RECURRING_VARIABLE) = Expense(
            id = "",
            account = account,
            title = "",
            category = null,
            year = year,
            type = type,
            period = null,
            subStatus = "Active",
            projected = null,
            tags = emptyList(),
            amounts = Month.ALL.associateWith { null },
            paid = Month.ALL.associateWith { false },
        )
    }
}
