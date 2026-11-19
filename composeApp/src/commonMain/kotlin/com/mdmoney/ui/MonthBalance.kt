package com.mdmoney.ui

import com.mdmoney.domain.Expense
import com.mdmoney.domain.Month

/** Where the account's balance stands when a month begins and when it ends. */
data class MonthBalance(val start: Double, val end: Double)

/**
 * The checkbook balance at the start and end of [month] of [year], given the year's [initial]
 * (opening) balance — the same model as the account balance: only money flagged *paid* /
 * *received* has moved, so a bill not yet paid does not lower either figure.
 *
 * The start is [initial] plus everything received minus everything paid in the months before
 * [month]; the end adds [month]'s own movement. So December's end equals the account's balance, and
 * each month's start equals the previous month's end.
 */
fun monthBalance(expenses: List<Expense>, year: Int, month: Month, initial: Double): MonthBalance {
    val rows = expenses.filter { it.year == year }
    fun net(m: Month): Double = rows.sumOf { e ->
        if (!e.isPaid(m)) 0.0 else (e.amount(m) ?: 0.0).let { if (e.isIncome) it else -it }
    }
    val start = initial + Month.ALL.filter { it.number < month.number }.sumOf(::net)
    return MonthBalance(start = start, end = start + net(month))
}
