package com.mdmoney.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountsTest {

    @Test
    fun display_always_shows_two_decimals_with_the_chosen_separator() {
        assertEquals("1234.50", formatMoney(1234.5, '.'))
        assertEquals("1234,50", formatMoney(1234.5, ','))
        assertEquals("390.00", formatMoney(390.0, '.'))
        assertEquals("390,00", formatMoney(390.0, ','))
        assertEquals("92.62", formatMoney(92.62, '.'))
        assertEquals("-15,00", formatMoney(-15.0, ','))
        assertEquals("0.00", formatMoney(0.0, '.'))
        // -0.00 must not render a stray minus sign.
        assertEquals("0.00", formatMoney(-0.0, '.'))
    }

    @Test
    fun input_prefill_keeps_natural_precision_but_honors_the_separator() {
        assertEquals("390", formatInput(390.0, '.'))
        assertEquals("390", formatInput(390.0, ','))
        assertEquals("1232.5", formatInput(1232.5, '.'))
        assertEquals("1232,5", formatInput(1232.5, ','))
        assertEquals("92,62", formatInput(92.62, ','))
    }

    @Test
    fun frontmatter_format_is_unaffected_by_display_separator() {
        // formatAmount is storage-only: always a dot, trailing zeros trimmed.
        assertEquals("390", formatAmount(390.0))
        assertEquals("1232.5", formatAmount(1232.5))
        assertEquals("92.62", formatAmount(92.62))
    }

    @Test
    fun round_trips_through_parse_regardless_of_separator() {
        assertEquals(1234.5, parseAmount(formatMoney(1234.5, ',')))
        assertEquals(1234.5, parseAmount(formatMoney(1234.5, '.')))
        assertEquals(92.62, parseAmount(formatInput(92.62, ',')))
    }
}
