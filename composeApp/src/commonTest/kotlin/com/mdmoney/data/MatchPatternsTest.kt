package com.mdmoney.data

import com.mdmoney.domain.Category
import com.mdmoney.domain.LedgerEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MatchPatternsTest {

    private val links = VaultLinks.of(
        categories = listOf(Category("groceries", "Groceries")),
        accountTitles = mapOf("Regions" to "Regions"),
    )

    // A ledger with a `match:` list, an unknown key and prose around the table.
    private val ledger = """
        ---
        title: Groceries
        category: "[[groceries|Groceries]]"
        match:
          - "Card Purchase Publix*"
          - 'PIN Purchase Wal-Mart*'
          - "/^pin purchase walmart \\d+/"
        account: "[[Regions|Regions]]"
        year: 2026
        month: September
        total: 55.54
        shop_notes: weekly
        ---

        Feira da semana.

        | Date     | Note   | Amount |
        | -------- | ------ | ------ |
        | 20260908 | Publix | 55.54  |

        Fim.
    """.trimIndent() + "\n"

    @Test
    fun reads_block_list_items_quoted_either_way_and_unescapes_them() {
        assertEquals(
            listOf("Card Purchase Publix*", "PIN Purchase Wal-Mart*", """/^pin purchase walmart \d+/"""),
            MatchPatterns.read(ledger).map { it.text },
        )
        val parsed = LedgerMapper.read("2026 Sep - Groceries", "Regions", ledger, 2026)!!
        assertEquals(3, parsed.patterns.size)
        assertTrue(parsed.patterns[2].matches("PIN Purchase Walmart 42 Malabar"), "the regex's \\d survived the YAML quoting")
        assertEquals(3, parsed.toExpense().patterns.size, "the projection keeps them for the import")
    }

    @Test
    fun reads_inline_and_single_value_forms() {
        val inline = "---\ntitle: X\nmatch: [\"Starbucks*\", 'Dunkin, Inc*', Chick-Fil-A*]\n---\n"
        assertEquals(listOf("Starbucks*", "Dunkin, Inc*", "Chick-Fil-A*"), MatchPatterns.read(inline).map { it.text })
        val single = "---\ntitle: X\nmatch: \"*publix*\"\n---\n"
        assertEquals(listOf("*publix*"), MatchPatterns.read(single).map { it.text })
        assertEquals(emptyList(), MatchPatterns.read("---\ntitle: X\n---\n"))
    }

    @Test
    fun saving_a_ledger_keeps_its_match_list_exactly() {
        val parsed = LedgerMapper.read("2026 Sep - Groceries", "Regions", ledger, 2026)!!
        assertEquals(ledger, LedgerMapper.write(ledger, parsed, links), "write(read(x)) == x")
        val grown = LedgerMapper.write(ledger, parsed.copy(entries = parsed.entries + LedgerEntry("20260910", "Publix", 10.0)), links)
        assertEquals(MatchPatterns.read(ledger), MatchPatterns.read(grown))
        assertTrue(grown.contains("shop_notes: weekly") && grown.contains("Feira da semana.") && grown.contains("Fim."))
    }

    @Test
    fun saving_a_plain_note_keeps_its_match_list_exactly() {
        val plain = """
            ---
            title: Internet
            category: "[[groceries|Groceries]]"
            match:
              - "Recurring Card Transaction Blue Stream*"
            account: "[[Regions|Regions]]"
            year: 2026
            type: recurring-variable
            renewal_date: 2026-12-01
        """.trimIndent() + "\n" +
            com.mdmoney.domain.Month.ALL.joinToString("") { "${it.key}: 84.94\n${it.paidKey}: false\n" } +
            "---\n\nBlue Stream fiber.\n"
        val e = ExpenseMapper.read("Internet - 2026", "Regions", plain, 2026)
        assertEquals(listOf("Recurring Card Transaction Blue Stream*"), e.patterns.map { it.text })
        assertEquals(plain, ExpenseMapper.write(plain, e, e, links), "write(read(x)) == x")
    }

    @Test
    fun adding_appends_only_new_patterns_and_leaves_the_rest_of_the_note_alone() {
        val out = MatchPatterns.withAdded(ledger, listOf("card purchase   PUBLIX*", "Card Purchase Aldi*"))
        // The existing items keep their exact lines (single quotes included); the duplicate (same
        // pattern up to case and spacing) is not added again.
        assertEquals(ledger.replace("  - \"/^pin purchase walmart \\\\d+/\"\n", "  - \"/^pin purchase walmart \\\\d+/\"\n  - \"Card Purchase Aldi*\"\n"), out)
        assertSame(ledger, MatchPatterns.withAdded(ledger, listOf("Card Purchase Publix*")), "nothing new: not rewritten")
    }

    @Test
    fun adding_to_a_note_without_a_list_creates_it_after_the_category_and_quotes_safely() {
        val note = "---\ntitle: Received\ntype: income\ncategory: \"[[Salário|Salário]]\"\nyear: 2026\nmonth: July\ntotal: 1\n---\n\nbody\n"
        val out = MatchPatterns.withAdded(note, listOf("'deel, Payments*", "say \"hi\" \\ there*"))
        assertEquals(
            "---\ntitle: Received\ntype: income\ncategory: \"[[Salário|Salário]]\"\nmatch:\n" +
                "  - \"'deel, Payments*\"\n  - \"say \\\"hi\\\" \\\\ there*\"\n" +
                "year: 2026\nmonth: July\ntotal: 1\n---\n\nbody\n",
            out,
        )
        assertEquals(listOf("'deel, Payments*", "say \"hi\" \\ there*"), MatchPatterns.read(out).map { it.text }, "reads back as written")
    }
}
