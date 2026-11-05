package com.mdmoney.data

/**
 * One statement that was imported into an account, kept in the account note's `imports:` list so the
 * user can tell which files they already brought in.
 *
 * It is stored as a single quoted string per item — `"file.pdf | 2026-07-24 | 2026-08-21 | 2026-11-05 | 24"`
 * (file, period start, period end, imported on, lines added) — which reads naturally in Obsidian and
 * needs nothing beyond the block lists the frontmatter parser already preserves. A period the
 * statement did not reveal is written `-`.
 */
data class ImportRecord(
    val fileName: String,
    /** First day the statement covers, `yyyy-MM-dd`; null when unknown. */
    val from: String?,
    val to: String?,
    /** The day it was imported, `yyyy-MM-dd`. */
    val on: String,
    /** How many lines were written (entries, plus lines added to existing notes). */
    val added: Int,
) {
    fun encode(): String =
        listOf(clean(fileName), from ?: NONE, to ?: NONE, on, added.toString()).joinToString(SEP)

    companion object {
        private const val SEP = " | "
        private const val NONE = "-"

        /** Quotes and backslashes would need YAML escaping; a file name loses nothing by swapping them. */
        private fun clean(name: String) = name.replace('"', '\'').replace('\\', '/').replace('\n', ' ').trim()

        /** Reads one item as [encode] wrote it; null for anything else, so foreign lines never crash. */
        fun decode(raw: String): ImportRecord? {
            val parts = raw.trim().removePrefix("-").trim().removeSurrounding("\"").split(SEP)
            if (parts.size < 5) return null
            val tail = parts.takeLast(4)
            val added = tail[3].trim().toIntOrNull() ?: return null
            val name = parts.dropLast(4).joinToString(SEP).trim()
            if (name.isEmpty()) return null
            fun day(v: String) = v.trim().takeIf { it != NONE && it.isNotEmpty() }
            return ImportRecord(name, day(tail[0]), day(tail[1]), tail[2].trim(), added)
        }

        /** `yyyy-MM-dd` for a calendar day. */
        fun iso(year: Int, month: Int, day: Int): String =
            "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}
