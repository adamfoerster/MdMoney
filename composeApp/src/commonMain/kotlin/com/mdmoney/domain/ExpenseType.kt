package com.mdmoney.domain

/**
 * What a line item is, and how it behaves across the twelve monthly slots.
 *
 * - [EVENTUAL]: a one-off expense, amount lands in a single month.
 * - [RECURRING_FIXED]: same amount every period (the app auto-fills the months).
 * - [RECURRING_VARIABLE]: recurs, but the amount is entered per month as the bill arrives.
 * - [INCOME]: money coming *in* rather than out (salary, bonus). Amounts are entered per month like
 *   [RECURRING_VARIABLE], and a month's `*-paid` flag means **received**. Income shares this field
 *   rather than a separate one, so it carries no recurrence of its own.
 */
enum class ExpenseType(val id: String) {
    EVENTUAL("eventual"),
    RECURRING_FIXED("recurring-fixed"),
    RECURRING_VARIABLE("recurring-variable"),
    INCOME("income");

    companion object {
        fun fromId(id: String?): ExpenseType? = entries.firstOrNull { it.id == id }
    }
}
