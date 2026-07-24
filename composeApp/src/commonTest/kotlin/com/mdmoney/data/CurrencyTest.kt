package com.mdmoney.data

import com.mdmoney.domain.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrencyTest {

    @Test
    fun presets_read_from_their_code_case_insensitively() {
        assertEquals(Currency.BRL, Currency.fromStored("BRL"))
        assertEquals(Currency.EUR, Currency.fromStored("eur"))
        assertEquals(Currency.USD, Currency.fromStored("  Usd "))
        assertEquals("R$", Currency.fromStored("BRL").symbol)
    }

    @Test
    fun a_non_preset_value_is_a_custom_symbol_carried_verbatim() {
        val kr = Currency.fromStored("kr")
        assertTrue(kr.isCustom)
        assertEquals("kr", kr.symbol)
        // A custom currency stores its raw symbol, not a code.
        assertEquals("kr", kr.stored())
    }

    @Test
    fun blank_or_absent_is_none_and_clears_the_key() {
        assertEquals(Currency.NONE, Currency.fromStored(null))
        assertEquals(Currency.NONE, Currency.fromStored("   "))
        assertNull(Currency.NONE.stored(), "NONE must clear the frontmatter key, not write a blank")
        assertEquals(Currency.NONE, Currency.custom("  "), "a blank custom symbol collapses to NONE")
    }

    @Test
    fun a_preset_stores_its_code() {
        assertEquals("BRL", Currency.BRL.stored())
        assertEquals("EUR", Currency.EUR.stored())
        assertEquals("USD", Currency.USD.stored())
    }
}
