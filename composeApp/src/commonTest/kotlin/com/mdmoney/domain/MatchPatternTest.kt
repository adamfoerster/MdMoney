package com.mdmoney.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchPatternTest {

    private fun p(text: String) = MatchPattern.of(text)!!

    @Test
    fun a_glob_must_match_the_whole_description_ignoring_case_and_spacing() {
        val publix = p("Card Purchase Publix*")
        assertTrue(publix.matches("Card Purchase Publix #1899 5411 Palm Bay FL 32907 3914"))
        assertTrue(publix.matches("  CARD   purchase publix"), "case and runs of spaces don't count")
        assertFalse(publix.matches("PIN Purchase Publix #1899"), "anchored at the start: not a 'contains'")
        assertTrue(p("*publix*").matches("PIN Purchase Publix #1899"), "a leading * makes it 'contains'")
        assertFalse(p("Starbucks").matches("Starbucks Store"), "without * it is the whole description")
    }

    @Test
    fun question_mark_is_one_character_and_wildcards_also_match_themselves() {
        assertTrue(p("Dl *Ub ????").matches("Dl *Ub 4121"))
        assertFalse(p("Dl *Ub ????").matches("Dl *Ub 41211"))
        // A description copied as-is, `*` and all, always matches its own line.
        assertTrue(p("Card Purchase Amazon Mktpl*8y").matches("Card Purchase Amazon Mktpl*8y"))
        assertTrue(p("Card Purchase Sq *Shake Shack*").matches("Card Purchase Sq *Shake Shack 5812 Orlando FL 32819 3914"))
    }

    @Test
    fun regex_is_written_between_slashes_and_searched_anywhere() {
        val walmart = p("/^pin purchase (wal-mart|walmart)/")
        assertTrue(walmart.matches("PIN Purchase Wal-Mart #5455 5411 Palm Bay 3914"))
        assertTrue(walmart.matches("PIN Purchase Walmart Malabar"))
        assertFalse(walmart.matches("Card Purchase Wal-Mart"))
        assertTrue(p("/sunpass/").matches("Card Purchase Sunpass*acc1442 4784"), "unanchored: a search")
        assertFalse(p("/([unclosed/").matches("anything"), "a broken regex matches nothing instead of failing")
    }

    @Test
    fun blank_items_are_not_patterns() {
        assertNull(MatchPattern.of("   "))
        assertNull(MatchPattern.of(""))
    }

    @Test
    fun specificity_counts_literal_characters() {
        assertEquals(20, p("Card Purchase Publix*").specificity)
        assertEquals(6, p("*publix*").specificity)
        assertEquals(1, p("?a??").specificity)
    }

    @Test
    fun learning_cuts_at_the_first_part_that_changes_between_purchases() {
        // Numbers: store, category code, ZIP, card digits.
        assertEquals("Card Purchase Publix*", MatchPattern.learnFrom("Card Purchase Publix #1899 5411 Palm Bay FL 32907 3914"))
        assertEquals("PIN Purchase Wal-Mart*", MatchPattern.learnFrom("PIN Purchase Wal-Mart #5455 5411 Palm Bay 3914"))
        assertEquals(
            "Recurring Card Transaction Blue Stream*",
            MatchPattern.learnFrom("Recurring Card Transaction Blue Stream 4899 954-753-0100 FL 33065 3914"),
        )
        // A reference after `*` that carries a digit: the merchant and its `*` stay.
        assertEquals("Card Purchase Amazon Mktpl*", MatchPattern.learnFrom("Card Purchase Amazon Mktpl*8y 5942 Amzn.Com/Bill WA 98109 3914"))
        assertEquals("Card Purchase Sunpass*", MatchPattern.learnFrom("Card Purchase Sunpass*acc1442 4784 888-865-5352 FL 33434 3914"))
        // A `*` with no digit after it is part of the merchant's name.
        assertEquals("Card Purchase Sq *Shake Shack*", MatchPattern.learnFrom("Card Purchase Sq *Shake Shack 5812 Orlando FL 32819 3914"))
        // A number joined to a word by a hyphen is a name, not a number.
        assertEquals("Card Purchase 7-Eleven*", MatchPattern.learnFrom("Card Purchase 7-Eleven 42314 5542 West Melbourn FL 32904 3914"))
    }

    @Test
    fun learning_also_cuts_at_codes_spelled_with_letters() {
        assertEquals("Card Purchase McDonald S*", MatchPattern.learnFrom("Card Purchase McDonald S F319 5814 Orlando FL 32819 3914"))
        assertEquals("Card Purchase Marriott*", MatchPattern.learnFrom("Card Purchase Marriott 337u4 3509 Miami FL 3914"))
        assertEquals("Card Purchase Amazon Reta*", MatchPattern.learnFrom("Card Purchase Amazon Reta* 4j 5331 Www.Amazon.CO WA 98109 3914"))
        assertEquals("PIN Purchase Target*", MatchPattern.learnFrom("PIN Purchase Target T-2547 5411 Melbourne 3914"))
        // A payroll reference: every deposit's differs, the payer doesn't.
        assertEquals("'deel,*", MatchPattern.learnFrom("'deel, It3qqlkc4 Payments Adam Foerster"))
        assertEquals("'deel,*", MatchPattern.learnFrom("'deel, I4wrvn5tw Payments Adam Foerster"))
        // A sign left before the cut is dropped.
        assertEquals("PIN Purchase Trader Joe S*", MatchPattern.learnFrom("PIN Purchase Trader Joe S # 5411 Miami FL 3914"))
        assertEquals("Card Purchase City Arts Mkt*", MatchPattern.learnFrom("Card Purchase City Arts Mkt @ 5999 Orlando FL 32827 3914"))
    }

    @Test
    fun learning_a_description_with_nothing_to_cut_still_takes_a_longer_line() {
        assertEquals("Fpl Direct Debit Elec Pymt Adam Foerster*", MatchPattern.learnFrom("Fpl Direct Debit Elec Pymt Adam Foerster"))
        assertTrue(p(MatchPattern.learnFrom("Fpl Direct Debit Elec Pymt Adam Foerster")!!)
            .matches("Fpl Direct Debit Elec Pymt Adam Foerster 8665298041 Webi"))
        assertEquals("Starbucks*", MatchPattern.learnFrom("Starbucks"))
    }

    @Test
    fun learning_never_produces_a_pattern_that_takes_every_line() {
        // Opening with a number would leave nothing before the cut: keep the description whole.
        assertEquals("12345 Transfer Ref*", MatchPattern.learnFrom("12345 Transfer Ref"))
        assertNull(MatchPattern.learnFrom("   "))
        assertFalse(p(MatchPattern.learnFrom("12345 Transfer Ref")!!).matches("Card Purchase Publix"))
    }
}
