package com.mdmoney.ui

import com.mdmoney.ui.screens.clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The reading screen's clock and estimate: a 3B model took ~45 s a page on a desktop CPU, and a
 * screen that sat on "page 2 of 7" for minutes was taken for a freeze and killed.
 */
class ReadingEstimateTest {

    private fun reading(done: Int, total: Int) = ImportPhase.Reading("x.pdf", done, total, loadingModel = false)

    @Test
    fun remaining_time_extrapolates_from_the_pages_done() {
        assertEquals(225, reading(done = 2, total = 7).remainingSeconds(elapsedSeconds = 90), "45 s a page × 5 pages left")
        assertEquals(0, reading(done = 7, total = 8).remainingSeconds(elapsedSeconds = 0))
    }

    @Test
    fun no_estimate_before_the_first_page_or_after_the_last() {
        assertNull(reading(done = 0, total = 7).remainingSeconds(elapsedSeconds = 30))
        assertNull(reading(done = 7, total = 7).remainingSeconds(elapsedSeconds = 300))
    }

    @Test
    fun the_clock_reads_minutes_and_seconds() {
        assertEquals("0:05", clock(5))
        assertEquals("2:15", clock(135))
        assertEquals("12:00", clock(720))
    }
}
