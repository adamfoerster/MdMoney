package com.mdmoney.ui

import com.mdmoney.importer.SimpleDate

/** Day of the week for a date, 0 = Sunday … 6 = Saturday (Sakamoto's method). */
fun dayOfWeek(year: Int, month: Int, day: Int): Int {
    val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val y = if (month < 3) year - 1 else year
    return (y + y / 4 - y / 100 + y / 400 + offsets[month - 1] + day) % 7
}

/**
 * The weeks of [month]/[year], Sunday first, each seven cells: the day of the month, or null for the
 * blanks before the 1st and after the last day.
 */
fun monthGrid(year: Int, month: Int): List<List<SimpleDate?>> {
    val lead = dayOfWeek(year, month, 1)
    val cells: List<SimpleDate?> =
        List(lead) { null } + (1..SimpleDate.daysIn(year, month)).map { SimpleDate(year, month, it) }
    return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
}
