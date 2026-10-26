package com.mdmoney.ui

import com.mdmoney.domain.Expense
import com.mdmoney.domain.Month

/** One month of the annual view: what came in, what went out, and the lines behind each. */
data class MonthDetail(
    val income: List<Expense>,
    val expenses: List<Expense>,
    val incomeTotal: Double,
    val expenseTotal: Double,
) {
    val net: Double get() = incomeTotal - expenseTotal
    val isEmpty: Boolean get() = income.isEmpty() && expenses.isEmpty()
}

/** What a line carries in [month], or across the whole year when [month] is null. */
fun Expense.valueIn(month: Month?): Double = if (month == null) total else amount(month) ?: 0.0

/**
 * The lines that carry money in [month] of [year] — or in the whole year when [month] is null, each
 * line then showing its yearly total — largest first, with the totals.
 */
fun monthDetail(expenses: List<Expense>, year: Int, month: Month?): MonthDetail {
    val rows = expenses.filter { it.year == year && it.valueIn(month) != 0.0 }
    val income = rows.filter { it.isIncome }.sortedByDescending { it.valueIn(month) }
    val out = rows.filter { !it.isIncome }.sortedByDescending { it.valueIn(month) }
    return MonthDetail(income, out, income.sumOf { it.valueIn(month) }, out.sumOf { it.valueIn(month) })
}

/** Total spent in [month] (the year when null), or null when nothing was spent so it can show a dash. */
fun monthSpent(expenses: List<Expense>, year: Int, month: Month?): Double? =
    monthDetail(expenses, year, month).takeIf { it.expenses.isNotEmpty() }?.expenseTotal
