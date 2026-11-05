package com.mdmoney.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewAccountFormTest {
    @Test fun blank_balance_means_set_it_later() {
        assertNull(openingBalanceFrom("", overdrawn = false))
        assertNull(openingBalanceFrom("   ", overdrawn = true))
        assertNull(openingBalanceFrom("abc", overdrawn = false))
    }

    @Test fun in_credit_keeps_the_typed_value() {
        assertEquals(2430.15, openingBalanceFrom("2430.15", overdrawn = false))
        assertEquals(2430.15, openingBalanceFrom("2430,15", overdrawn = false))
    }

    @Test fun overdrawn_negates_the_typed_value() {
        assertEquals(-120.5, openingBalanceFrom("120,5", overdrawn = true))
    }

    @Test fun the_toggle_decides_the_sign_not_a_typed_minus() {
        assertEquals(-50.0, openingBalanceFrom("-50", overdrawn = true))
        assertEquals(50.0, openingBalanceFrom("-50", overdrawn = false))
    }

    @Test fun overdrawn_zero_is_plain_zero() {
        // -0.0 would be written to the note as "-0".
        assertEquals("0.0", openingBalanceFrom("0", overdrawn = true).toString())
    }
}
