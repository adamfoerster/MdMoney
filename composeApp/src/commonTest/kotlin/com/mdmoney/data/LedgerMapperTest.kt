package com.mdmoney.data

import com.mdmoney.domain.Category
import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LedgerMapperTest {

    private val links = VaultLinks.of(
        categories = listOf(Category("food", "Alimentação")),
        accountTitles = mapOf("nubank" to "Nubank"),
    )

    // The exact shape requested, verbatim.
    private val sample = """
        ---
        title: Alimentação
        category: "[[food|Alimentação]]"
        account: "[[nubank|Nubank]]"
        year: 2026
        month: July
        total: 26.78
        ---

        | Date     | Note         | Amount |
        | -------- | ------------ | ------ |
        | 20260715 | Starbucks    | 10.23  |
        | 20260715 | Seven Eleven | 5.32   |
        | 20260714 | Starbucks    | 11.23  |
    """.trimIndent() + "\n"

    @Test
    fun reads_the_example_note() {
        assertTrue(LedgerMapper.isLedger(sample))
        val l = assertNotNull(LedgerMapper.read("2026 Jul - Alimentação", "nubank", sample, 2026))

        assertEquals("Alimentação", l.title)
        assertEquals("food", l.category, "the link's target is the category, not its display title")
        assertEquals(2026, l.year)
        assertEquals(Month.JUL, l.month)
        assertEquals(3, l.entries.size)
        assertEquals(LedgerEntry("20260715", "Starbucks", 10.23), l.entries[0])
        assertEquals(LedgerEntry("20260715", "Seven Eleven", 5.32), l.entries[1])
        assertEquals(LedgerEntry("20260714", "Starbucks", 11.23), l.entries[2])
        assertEquals(26.78, l.total, 0.001)
    }

    @Test
    fun a_plain_expense_note_is_not_a_ledger() {
        assertFalse(
            LedgerMapper.isLedger(
                """
                ---
                title: Contador
                year: 2026
                jan: 390
                jan-paid: true
                ---
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun round_trips_unchanged() {
        val l = assertNotNull(LedgerMapper.read("x", "nubank", sample, 2026))
        assertEquals(sample, LedgerMapper.write(sample, l, links))
    }

    /**
     * The vault predates links, so a plain `category: food` must be read the same and written
     * forward — that upgrade *is* the migration, and it runs through this writer.
     */
    @Test
    fun upgrades_a_legacy_note_to_links_without_disturbing_it() {
        val legacy = """
            ---
            title: Alimentação
            category: food
            conta: nubank
            year: 2026
            month: July
            total: 10.23
            ---

            | Date     | Note      | Amount |
            | -------- | --------- | ------ |
            | 20260715 | Starbucks | 10.23  |
        """.trimIndent() + "\n"

        val l = assertNotNull(LedgerMapper.read("x", "nubank", legacy, 2026))
        assertEquals("food", l.category, "a plain value reads as the same category a link does")

        val out = LedgerMapper.write(legacy, l, links)
        assertTrue(out.contains("""category: "[[food|Alimentação]]""""), out)
        assertTrue(out.contains("""account: "[[nubank|Nubank]]""""), out)
        assertTrue(out.contains("conta: nubank"), "the legacy key is left exactly as the user wrote it")
        assertTrue(out.contains("| 20260715 | Starbucks | 10.23  |"), "the table is none of this migration's business")

        // Running it twice must be the same as running it once.
        val again = LedgerMapper.write(out, assertNotNull(LedgerMapper.read("x", "nubank", out, 2026)), links)
        assertEquals(out, again)
    }

    /** A title tuned in Obsidian is the user's, and pointing at the right note is all the app asks. */
    @Test
    fun leaves_a_link_that_already_resolves_alone() {
        val tuned = sample.replace("""category: "[[food|Alimentação]]"""", """category: "[[food|Nossa comida]]"""")
        val l = assertNotNull(LedgerMapper.read("x", "nubank", tuned, 2026))
        assertEquals("food", l.category)
        assertEquals(tuned, LedgerMapper.write(tuned, l, links))
    }

    @Test
    fun appending_an_entry_reorders_newest_first_and_recomputes_total() {
        val l = assertNotNull(LedgerMapper.read("x", "nubank", sample, 2026))
        val updated = l.copy(entries = l.entries + LedgerEntry("20260716", "Padaria", 3.22))
        val out = LedgerMapper.write(sample, updated)

        assertTrue(out.contains("total: 30"), out) // 26.78 + 3.22, trailing zeros trimmed
        val rows = out.lines().filter { it.startsWith("| 2026") }
        assertEquals("| 20260716 | Padaria      | 3.22   |", rows.first())
        assertEquals(4, rows.size)

        // And it survives a second read.
        val reread = assertNotNull(LedgerMapper.read("x", "nubank", out, 2026))
        assertEquals(30.0, reread.total, 0.001)
        assertEquals("20260716", reread.entries.first().date)
    }

    @Test
    fun preserves_unknown_keys_and_prose_around_the_table() {
        val withProse = """
            ---
            title: Alimentação
            tags:
              - avulso
            month: July
            year: 2026
            total: 0
            ---

            Notas do mês.

            | Date     | Note      | Amount |
            | -------- | --------- | ------ |
            | 20260715 | Starbucks | 10.23  |

            Rodapé.
        """.trimIndent() + "\n"

        val l = assertNotNull(LedgerMapper.read("x", "nubank", withProse, 2026))
        val out = LedgerMapper.write(withProse, l.copy(entries = l.entries + LedgerEntry("20260716", "Café", 2.0)))

        assertTrue(out.contains("- avulso"), out)
        assertTrue(out.contains("Notas do mês."), out)
        assertTrue(out.contains("Rodapé."), out)
        assertTrue(out.contains("total: 12.23"), out)
        assertTrue(out.contains("| 20260716 | Café"), out)
    }

    @Test
    fun writes_a_brand_new_note_from_nothing() {
        val l = Ledger(
            id = Ledger.idFor(2026, Month.JUL, "Alimentação"),
            account = "nubank",
            title = "Alimentação",
            category = "food",
            year = 2026,
            month = Month.JUL,
            entries = listOf(LedgerEntry("20260715", "Starbucks", 10.23)),
        )
        assertEquals("2026 Jul - Alimentação", l.id)

        val out = LedgerMapper.write(null, l, links)
        assertTrue(out.startsWith("---\n"), out)
        assertTrue(out.contains("month: July"), out)
        assertTrue(out.contains("""category: "[[food|Alimentação]]""""), out)
        assertTrue(out.contains("""account: "[[nubank|Nubank]]""""), out)
        assertFalse(out.contains("conta:"), "a new note carries `account:`, not the legacy key")
        assertTrue(out.contains("total: 10.23"), out)
        assertTrue(out.contains("| Date     | Note      | Amount |"), out)
        assertTrue(out.contains("| 20260715 | Starbucks | 10.23  |"), out)

        // A fresh note must read back identical.
        val reread = assertNotNull(LedgerMapper.read(l.id, "nubank", out, 2026))
        assertEquals(l.entries, reread.entries)
        assertEquals(Month.JUL, reread.month)
    }
}
