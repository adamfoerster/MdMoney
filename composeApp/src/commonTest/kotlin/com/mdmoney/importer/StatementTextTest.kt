package com.mdmoney.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementTextTest {

    @Test
    fun amounts_parse_in_every_way_statements_print_them() {
        assertEquals(1234.56, StatementText.parseAmount("1.234,56"))
        assertEquals(5069.33, StatementText.parseAmount("5,069.33"))
        assertEquals(5879.28, StatementText.parseAmount("R$ 5.879,28"))
        assertEquals(-17.04, StatementText.parseAmount("- US$ 17,04"))
        assertEquals(-84.94, StatementText.parseAmount("(84.94)"))
        assertEquals(1.55, StatementText.parseAmount("1.55"))
        assertEquals(1234.0, StatementText.parseAmount("1,234"), "three digits after the only separator group thousands")
        assertEquals(48840.0, StatementText.parseAmount("+48.840,00"))
        assertNull(StatementText.parseAmount("R$"))
    }

    @Test
    fun an_amount_is_found_only_as_a_whole_number() {
        val page = "Uiclap - STONE IP S.A. 429,95\nTotal de saídas - 1.218,18"
        assertTrue(StatementText.containsAmount(page, 429.95))
        assertTrue(StatementText.containsAmount(page, 1218.18), "grouped thousands")
        assertFalse(StatementText.containsAmount(page, 218.18), "not the tail of 1.218,18")
        assertFalse(StatementText.containsAmount(page, 29.95), "not the tail of 429,95")
        assertTrue(StatementText.containsAmount("Card Credit 5,069.33", 5069.33))
        assertTrue(StatementText.containsAmount("PIN Purchase 64.00", 64.0))
        // Regression: squeezing out whitespace glued `170,40` to the next line's `26 AGO`.
        assertTrue(StatementText.containsAmount("Oticas R$ 170,40\n26 AGO Mercado R$ 166,92", 170.4))
    }

    @Test
    fun periods_are_read_from_each_statement_style() {
        assertEquals(
            StatementPeriod(SimpleDate(2026, 8, 22), SimpleDate(2026, 9, 22)),
            StatementText.detectPeriod("LIFEGREEN CHECKING\n August 22, 2026 through  September 22, 2026\nSUMMARY", monthFirst = true),
        )
        assertEquals(
            StatementPeriod(SimpleDate(2026, 8, 1), SimpleDate(2026, 8, 31)),
            StatementText.detectPeriod("01 DE AGOSTO DE 2026 a 31 DE AGOSTO DE 2026 VALORES EM R$"),
        )
        assertEquals(
            StatementPeriod(SimpleDate(2026, 7, 1), SimpleDate(2026, 7, 31)),
            StatementText.detectPeriod("Period of activity\nJuly, 1st 2026 of July, 31st 2026\nEnding balance"),
        )
    }

    @Test
    fun a_card_cycle_printed_without_years_takes_them_from_the_issue_date() {
        val bill = "FATURA 01 OUT 2026 EMISSÃO E ENVIO 24 SET 2026\nTRANSAÇÕES DE 25 AGO A 24 SET\n"
        // The only full dates precede the cycle, so the last one anchors it.
        assertEquals(
            StatementPeriod(SimpleDate(2026, 8, 25), SimpleDate(2026, 9, 24)),
            StatementText.detectPeriod(bill),
        )
        // A cycle over the new year: December's purchases belong to the year before the issue date.
        assertEquals(
            StatementPeriod(SimpleDate(2025, 12, 25), SimpleDate(2026, 1, 24)),
            StatementText.detectPeriod("TRANSAÇÕES DE 25 DEZ A 24 JAN\nEMISSÃO E ENVIO 24 JAN 2026"),
        )
    }

    @Test
    fun a_missing_year_is_settled_by_the_period() {
        val crossing = StatementPeriod(SimpleDate(2025, 12, 25), SimpleDate(2026, 1, 24))
        assertEquals(2025, StatementText.resolveYear(12, crossing, fallbackYear = 2030))
        assertEquals(2026, StatementText.resolveYear(1, crossing, fallbackYear = 2030))
        val plain = StatementPeriod(SimpleDate(2026, 8, 25), SimpleDate(2026, 9, 24))
        assertEquals(2026, StatementText.resolveYear(8, plain, fallbackYear = 2030))
        assertEquals(2030, StatementText.resolveYear(8, null, fallbackYear = 2030))
    }

    @Test
    fun currency_and_date_order_come_from_the_text() {
        assertEquals("BRL", StatementText.detectCurrency("R$ 170,40 · limite US$ 10"))
        assertEquals("USD", StatementText.detectCurrency("- US$ 17,04"))
        assertEquals("USD", StatementText.detectCurrency("Beginning Balance \$1,113.24"))
        assertNull(StatementText.detectCurrency("no money here"))

        assertTrue(StatementText.isMonthFirst("07/25/2026 - 6:25 PM", currency = null), "25 can't be a month")
        assertFalse(StatementText.isMonthFirst("emissão (24/09/2026)", currency = "USD"), "24 can't be a month")
        assertTrue(StatementText.isMonthFirst("08/04 Card Purchase", currency = "USD"), "ambiguous: follows the currency")
        assertFalse(StatementText.isMonthFirst("08/04 Compra", currency = "BRL"))
    }

    @Test
    fun month_names_in_three_languages() {
        assertEquals(8, StatementText.monthNumber("AGO"))
        assertEquals(9, StatementText.monthNumber("set"))
        assertEquals(9, StatementText.monthNumber("September"))
        assertEquals(3, StatementText.monthNumber("Março"))
        assertEquals(12, StatementText.monthNumber("dic."))
        assertNull(StatementText.monthNumber("Total"))
    }
}
