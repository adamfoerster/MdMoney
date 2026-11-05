package com.mdmoney.ui

import com.mdmoney.data.ImportRecord
import com.mdmoney.importer.SimpleDate
import com.mdmoney.importer.StatementPeriod

/**
 * What to remember about an import: the statement's own period when it printed one, otherwise the
 * span of the lines that were actually imported (null when there were none to span).
 */
fun importRecordFor(
    fileName: String,
    period: StatementPeriod?,
    importedDates: List<SimpleDate>,
    today: SimpleDate,
    added: Int,
): ImportRecord {
    val start = period?.start ?: importedDates.minOrNull()
    val end = period?.end ?: importedDates.maxOrNull()
    fun iso(d: SimpleDate?) = d?.let { ImportRecord.iso(it.year, it.month, it.day) }
    return ImportRecord(fileName, iso(start), iso(end), iso(today)!!, added)
}

/** The earlier import of a file with this name, so the review can say it came in before. */
fun previousImport(recent: List<ImportRecord>, fileName: String): ImportRecord? =
    recent.firstOrNull { it.fileName == fileName }

private fun parseDay(iso: String?): Triple<Int, Int, Int>? {
    val p = iso?.split("-")?.takeIf { it.size == 3 } ?: return null
    val (y, m, d) = p.map { it.toIntOrNull() ?: return null }
    return if (m in 1..12 && d in 1..31) Triple(y, m, d) else null
}

/** `5 Nov 2026`, or the text as stored when it isn't a `yyyy-MM-dd` day. */
fun importDayLabel(iso: String, monthShort: (Int) -> String): String =
    parseDay(iso)?.let { (y, m, d) -> "$d ${monthShort(m)} $y" } ?: iso

/**
 * `24 Jul – 21 Aug 2026`: the year is written once when both ends share it, and each end carries its
 * own when they don't. Null when the statement's period is unknown.
 */
fun importPeriodLabel(from: String?, to: String?, monthShort: (Int) -> String): String? {
    val a = parseDay(from)
    val b = parseDay(to)
    if (a == null || b == null) return null
    if (a == b) return importDayLabel(to!!, monthShort)
    return if (a.first == b.first) {
        "${a.third} ${monthShort(a.second)} – ${b.third} ${monthShort(b.second)} ${b.first}"
    } else {
        "${importDayLabel(from!!, monthShort)} – ${importDayLabel(to!!, monthShort)}"
    }
}
