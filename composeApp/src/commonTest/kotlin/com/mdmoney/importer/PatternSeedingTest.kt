package com.mdmoney.importer

import kotlin.test.Test
import kotlin.test.assertEquals

class PatternSeedingTest {

    private fun ledger(title: String, month: String, rows: List<String>, extra: String = "", income: Boolean = false) =
        "---\ntitle: $title\n" + (if (income) "type: income\n" else "") + extra +
            "year: 2026\nmonth: $month\ntotal: 1\n---\n\n| Date | Note | Amount |\n| -- | -- | -- |\n" +
            rows.joinToString("") { "| 20260901 | $it | 1 |\n" }

    @Test
    fun each_note_learns_from_its_own_rows_without_taking_another_groups_lines() {
        val plan = PatternSeeding.plan(
            listOf(
                PatternSeeding.Note(
                    "2026 Sep - Groceries",
                    ledger(
                        "Groceries", "September",
                        listOf("Card Purchase Publix #1899 5411", "Card Purchase Publix #1899 5411", "Card Purchase Amazon Mktpl*8y 5942", "PIN Purchase Aldi 72"),
                        extra = "match:\n  - \"*aldi*\"\n",
                    ),
                ),
                PatternSeeding.Note("2026 Sep - Shopping", ledger("Shopping", "September", listOf("Card Purchase Amazon Mktpl*n4 5942"))),
                // Same group, another month: not "elsewhere".
                PatternSeeding.Note("2026 Oct - Groceries", ledger("Groceries", "October", listOf("Card Purchase Publix #2000 5411"))),
                PatternSeeding.Note("2026 Sep - Received", ledger("Received", "September", listOf("'deel, Ij6u0h33k Payments Adam Foerster"), income = true)),
            ),
        )
        assertEquals(
            mapOf(
                // Publix once (the second row is already taken); Amazon spelled out, since the short
                // form would take Shopping's line; Aldi already covered by the hand-written pattern.
                "2026 Sep - Groceries" to listOf("Card Purchase Publix*", "Card Purchase Amazon Mktpl*8y 5942*"),
                "2026 Sep - Shopping" to listOf("Card Purchase Amazon Mktpl*n4 5942*"),
                "2026 Oct - Groceries" to listOf("Card Purchase Publix*"),
                "2026 Sep - Received" to listOf("'deel,*"),
            ),
            plan,
        )
    }
}
