package com.mdmoney.ui

import com.mdmoney.ui.i18n.EnStrings
import com.mdmoney.ui.i18n.PtStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class EntryDateTest {

    @Test
    fun renders_the_stored_date_as_mono_meta() {
        assertEquals("15 JUL", formatEntryDate("20260715", EnStrings))
        assertEquals("15 JUL", formatEntryDate("20260715", PtStrings))
        assertEquals("01 JAN", formatEntryDate("20260101", EnStrings), "the day keeps two digits")
        assertEquals("31 DEZ", formatEntryDate("20261231", PtStrings), "month name follows the language")
    }

    /** A hand-edited note must still show its row rather than lose it to a formatter. */
    @Test
    fun passes_through_anything_that_is_not_a_stored_date() {
        assertEquals("15/07/2026", formatEntryDate("15/07/2026", EnStrings))
        assertEquals("", formatEntryDate("", EnStrings))
        assertEquals("2026071", formatEntryDate("2026071", EnStrings), "too short")
        assertEquals("20261315", formatEntryDate("20261315", EnStrings), "month 13")
        assertEquals("20260700", formatEntryDate("20260700", EnStrings), "day 0")
    }
}
