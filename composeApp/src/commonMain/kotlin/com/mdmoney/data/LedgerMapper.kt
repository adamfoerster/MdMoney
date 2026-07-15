package com.mdmoney.data

import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month

/**
 * Converts between a ledger note on disk and the [Ledger] model.
 *
 * The note is frontmatter plus a markdown table of purchases:
 *
 * ```
 * ---
 * title: Alimentação
 * category: food
 * conta: nubank
 * year: 2026
 * month: July
 * total: 26.78
 * ---
 *
 * | Date     | Note      | Amount |
 * | -------- | --------- | ------ |
 * | 20260715 | Starbucks | 10.23  |
 * ```
 *
 * A note is recognised as a ledger by its `month:` key (a plain expense note never has one), which
 * is what keeps existing vaults readable. Unknown frontmatter keys and any prose around the table
 * are preserved; `total` is always recomputed from the rows so the two can't drift apart.
 */
object LedgerMapper {

    private const val KEY_MONTH = "month"
    private const val KEY_TOTAL = "total"

    /** True when [content] parses as a ledger rather than a plain expense note. */
    fun isLedger(content: String): Boolean = isLedger(FrontmatterParser.parse(content))

    fun isLedger(note: MarkdownNote): Boolean = Month.fromEnglish(note.scalar(KEY_MONTH)) != null

    fun read(id: String, account: String, content: String, fallbackYear: Int): Ledger? {
        val note = FrontmatterParser.parse(content)
        val month = Month.fromEnglish(note.scalar(KEY_MONTH)) ?: return null
        return Ledger(
            id = id,
            account = account,
            title = note.scalar("title") ?: id,
            category = note.scalar("category"),
            year = note.scalar("year")?.toIntOrNull() ?: fallbackYear,
            month = month,
            entries = readTable(note.body),
        )
    }

    /**
     * Renders [ledger] back to markdown. Pass [originalContent] to keep unknown frontmatter and any
     * prose the user wrote around the table; pass null for a new note.
     */
    fun write(originalContent: String?, ledger: Ledger): String {
        val note = originalContent?.let { FrontmatterParser.parse(it) }
            ?: MarkdownNote(mutableListOf(), "")

        note.setScalar("title", ledger.title)
        if (ledger.category != null || note.hasKey("category")) note.setScalar("category", ledger.category)
        note.setScalar("conta", ledger.account)
        note.setScalar("year", ledger.year.toString())
        note.setScalar(KEY_MONTH, ledger.month.english)
        note.setScalar(KEY_TOTAL, formatAmount(ledger.total))

        val rebuilt = MarkdownNote(note.entries, replaceTable(note.body, renderTable(ledger.sorted())))
        return FrontmatterParser.serialize(rebuilt)
    }

    // --- table ---

    /** Reads `| date | note | amount |` rows, skipping the header and its `---` separator. */
    private fun readTable(body: String): List<LedgerEntry> {
        val out = mutableListOf<LedgerEntry>()
        for (line in body.replace("\r\n", "\n").split("\n")) {
            val cells = splitRow(line) ?: continue
            if (cells.size < 3) continue
            if (cells.all { it.isSeparator() }) continue
            val amount = parseAmount(cells[2]) ?: continue // header row has no numeric amount
            out.add(LedgerEntry(date = cells[0], note = cells[1], amount = amount))
        }
        return out
    }

    /** Splits a markdown table row into trimmed cells, or null when the line isn't a row. */
    private fun splitRow(line: String): List<String>? {
        val t = line.trim()
        if (!t.startsWith("|")) return null
        return t.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
    }

    private fun String.isSeparator(): Boolean = isNotEmpty() && all { it == '-' || it == ':' || it == ' ' }

    private fun renderTable(entries: List<LedgerEntry>): String {
        val rows = entries.map { listOf(it.date, it.note, formatAmount(it.amount)) }
        val header = listOf(COL_DATE, COL_NOTE, COL_AMOUNT)
        // Pad every column to its widest cell so the table stays readable in Obsidian.
        val widths = (0..2).map { i -> (rows + listOf(header)).maxOf { it[i].length } }
        fun row(cells: List<String>) =
            cells.mapIndexed { i, c -> c.padEnd(widths[i]) }.joinToString(" | ", prefix = "| ", postfix = " |")

        return buildString {
            append(row(header)).append('\n')
            append(widths.joinToString(" | ", prefix = "| ", postfix = " |") { "-".repeat(it) }).append('\n')
            rows.forEach { append(row(it)).append('\n') }
        }
    }

    /**
     * Swaps the contiguous run of table lines in [body] for [table], leaving any prose before or
     * after it untouched. When the body has no table yet, the table is appended.
     */
    private fun replaceTable(body: String, table: String): String {
        val lines = body.replace("\r\n", "\n").split("\n")
        val first = lines.indexOfFirst { splitRow(it) != null }
        if (first < 0) {
            val prose = body.trimEnd('\n')
            return if (prose.isBlank()) "\n$table" else "$prose\n\n$table"
        }
        var last = first
        while (last + 1 < lines.size && splitRow(lines[last + 1]) != null) last++
        // Each preceding line keeps its own terminator, so the blank line before the table survives.
        val before = if (first == 0) "" else lines.take(first).joinToString("\n") + "\n"
        val after = lines.drop(last + 1).joinToString("\n")
        return before + table.trimEnd('\n') + (if (after.isEmpty()) "\n" else "\n$after")
    }

    private const val COL_DATE = "Date"
    private const val COL_NOTE = "Note"
    private const val COL_AMOUNT = "Amount"
}
