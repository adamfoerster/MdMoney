package com.mdmoney.data

import com.mdmoney.importer.SimpleDate
import com.mdmoney.importer.StatementPeriod
import com.mdmoney.ui.importDayLabel
import com.mdmoney.ui.importPeriodLabel
import com.mdmoney.ui.importRecordFor
import com.mdmoney.ui.previousImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The account note gained an `imports:` list. Like every vault-format change it must round-trip, and
 * leave unknown keys and the prose body exactly as the user (or Obsidian) left them.
 */
class ImportHistoryTest {

    private val note = buildString {
        appendLine("---")
        appendLine("type: account")
        appendLine("account: nubank")
        appendLine("title: Nubank")
        appendLine("color: \"#8a1f1f\"") // an unknown key
        appendLine("initial-2026: 1200")
        appendLine("---")
        append("\nNotas sobre a conta [[Nubank]].\n")
    }

    private val first = ImportRecord("Fatura_08.pdf", "2026-07-24", "2026-08-21", "2026-11-05", 24)
    private val second = ImportRecord("Fatura_09.pdf", "2026-08-22", "2026-09-21", "2026-11-06", 31)

    @Test
    fun a_record_round_trips_through_its_line() {
        assertEquals(first, ImportRecord.decode("  - \"${first.encode()}\""))
        val noPeriod = ImportRecord("a | b.pdf", null, null, "2026-11-05", 0)
        assertEquals(noPeriod, ImportRecord.decode(noPeriod.encode()))
    }

    @Test
    fun a_foreign_line_does_not_decode() {
        assertNull(ImportRecord.decode("  - something else"))
        assertNull(ImportRecord.decode("  - \"x.pdf | 2026-01-01 | 2026-01-31 | 2026-02-01 | many\""))
    }

    @Test
    fun recording_an_import_keeps_unknown_keys_body_and_balance() {
        val out = AccountMeta.read("nubank", note).withImport(first).serialize()

        assertTrue(out.contains("imports:\n  - \"Fatura_08.pdf | 2026-07-24 | 2026-08-21 | 2026-11-05 | 24\"\n"), out)
        assertTrue(out.contains("color: \"#8a1f1f\""), out)
        assertTrue(out.contains("initial-2026: 1200"), out)
        assertTrue(out.endsWith("\nNotas sobre a conta [[Nubank]].\n"), out)

        val reread = AccountMeta.read("nubank", out)
        assertEquals(listOf(first), reread.imports)
        assertEquals(1200.0, reread.initialBalance(2026))
        // Writing what was read changes nothing.
        assertEquals(out, reread.serialize())
    }

    @Test
    fun the_newest_import_comes_first() {
        val out = AccountMeta.read("nubank", note).withImport(first).serialize()
        val twice = AccountMeta.read("nubank", out).withImport(second)
        assertEquals(listOf(second, first), twice.imports)
    }

    @Test
    fun importing_the_same_file_again_replaces_its_entry() {
        val again = first.copy(on = "2026-11-09", added = 3)
        val meta = AccountMeta.read("nubank", note).withImport(first).withImport(second).withImport(again)
        assertEquals(listOf(again, second), meta.imports)
    }

    @Test
    fun the_list_is_capped_and_keeps_the_newest() {
        var meta = AccountMeta.read("nubank", note)
        repeat(AccountMeta.MAX_IMPORTS + 5) { i -> meta = meta.withImport(first.copy(fileName = "f$i.pdf")) }
        assertEquals(AccountMeta.MAX_IMPORTS, meta.imports.size)
        assertEquals("f${AccountMeta.MAX_IMPORTS + 4}.pdf", meta.imports.first().fileName)
    }

    @Test
    fun a_note_with_no_imports_reads_as_empty() {
        assertEquals(emptyList(), AccountMeta.read("nubank", note).imports)
        assertEquals(emptyList(), AccountMeta.read("nubank", null).imports)
    }

    @Test
    fun the_statement_period_wins_over_the_imported_lines() {
        val period = StatementPeriod(SimpleDate(2026, 7, 24), SimpleDate(2026, 8, 21))
        val r = importRecordFor("a.pdf", period, listOf(SimpleDate(2026, 8, 1)), SimpleDate(2026, 11, 5), 2)
        assertEquals(ImportRecord("a.pdf", "2026-07-24", "2026-08-21", "2026-11-05", 2), r)
    }

    @Test
    fun without_a_printed_period_the_lines_span_it() {
        val dates = listOf(SimpleDate(2026, 8, 9), SimpleDate(2026, 7, 30), SimpleDate(2026, 8, 2))
        val r = importRecordFor("a.pdf", null, dates, SimpleDate(2026, 11, 5), 3)
        assertEquals("2026-07-30", r.from)
        assertEquals("2026-08-09", r.to)
        val none = importRecordFor("a.pdf", null, emptyList(), SimpleDate(2026, 11, 5), 0)
        assertNull(none.from)
    }

    @Test
    fun finds_an_earlier_import_by_file_name() {
        assertEquals(second, previousImport(listOf(second, first), "Fatura_09.pdf"))
        assertNull(previousImport(listOf(first), "other.pdf"))
    }

    @Test
    fun period_labels() {
        val m = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val short = { n: Int -> m[n - 1] }
        assertEquals("24 Jul – 21 Aug 2026", importPeriodLabel("2026-07-24", "2026-08-21", short))
        assertEquals("24 Dec 2025 – 21 Jan 2026", importPeriodLabel("2025-12-24", "2026-01-21", short))
        assertEquals("21 Aug 2026", importPeriodLabel("2026-08-21", "2026-08-21", short))
        assertNull(importPeriodLabel(null, "2026-08-21", short))
        assertEquals("5 Nov 2026", importDayLabel("2026-11-05", short))
    }
}
