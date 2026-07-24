package com.mdmoney.ui

import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month

/** The Home screen's three blocks for one month, each already sorted for display. */
data class MonthLines(
    val income: List<Expense>,
    val recurring: List<Expense>,
    val eventual: List<Expense>,
) {
    val isEmpty: Boolean get() = income.isEmpty() && recurring.isEmpty() && eventual.isEmpty()
}

/**
 * Splits [expenses] into the lines Home shows for [month] of [year].
 *
 * What belongs to a month turns on **ledger vs plain note**, not on whether the month has a value:
 *
 * - A **plain note** is a plan for the year — twelve slots and twelve paid flags — so it shows every
 *   month, with or without a value there. A yearly bill like `Seguro residencial` carries its amount
 *   in January alone, and hiding it for the other eleven months made it look lost; the same goes for
 *   a recurring bill whose amount you only enter once it arrives.
 * - A **ledger** is the record of one month's purchases, so it shows only in that month. It has no
 *   value in any other month, and listing an empty `2026 Jun - Alimentação` under July would be
 *   claiming a purchase that never happened.
 */
fun monthLines(expenses: List<Expense>, year: Int, month: Month): MonthLines {
    val rows = expenses.filter { it.year == year }.sortedBy { it.title.lowercase() }
    return MonthLines(
        income = rows.filter { it.isIncome },
        recurring = rows.filter {
            it.type == ExpenseType.RECURRING_FIXED || it.type == ExpenseType.RECURRING_VARIABLE
        },
        eventual = rows.filter {
            it.type == ExpenseType.EVENTUAL && (!it.ledger || it.amount(month) != null)
        },
    )
}
