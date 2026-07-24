package com.mdmoney.ui

import com.mdmoney.domain.Month
import com.mdmoney.ui.i18n.Strings

/**
 * A ledger entry's stored `YYYYMMDD` date as the brand's mono meta — `15 JUL`.
 *
 * Storage format is not display format: the table in the note keeps the sortable eight digits, and
 * only the screen sees this. A value that isn't a well-formed date (a note hand-edited in Obsidian)
 * is passed through as written rather than dropped, so nothing silently disappears from the list.
 */
fun formatEntryDate(date: String, strings: Strings): String {
    val digits = date.trim()
    if (digits.length != 8 || !digits.all { it.isDigit() }) return date
    val month = Month.fromNumber(digits.substring(4, 6).toInt()) ?: return date
    val day = digits.substring(6, 8).toInt()
    if (day !in 1..31) return date
    return "${day.toString().padStart(2, '0')} ${strings.monthShort(month)}".uppercase()
}
