package com.mdmoney.data

import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrontmatterRoundTripTest {

    // A real Nubank note: block-list tags, unknown keys (billing_cycle/renewal_date), the legacy
    // Portuguese `fev` key, all months fixed at 390, plus a body with a wikilink.
    private val content = buildString {
        appendLine("---")
        appendLine("tags:")
        appendLine("  - subscription")
        appendLine("title: Contador")
        appendLine("category: empresa")
        appendLine("billing_cycle: monthly")
        appendLine("renewal_date: 2026-05-04")
        appendLine("subStatus: Active")
        appendLine("conta: Nubank")
        appendLine("year: 2026")
        for (m in listOf("jan", "fev", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")) {
            appendLine("$m: 390")
        }
        for (m in listOf("jan", "fev", "mar", "apr", "may", "jun")) appendLine("$m-paid: true")
        appendLine("jul-paid: true")
        for (m in listOf("aug", "sep", "oct", "nov", "dec")) appendLine("$m-paid: false")
        appendLine("period: Mensal")
        appendLine("---")
        append("\nAnotações sobre o [[Contador]].\n")
    }

    @Test
    fun reads_legacy_fev_and_infers_fixed() {
        val e = ExpenseMapper.read("Collegato - Contador", "Nubank", content, fallbackYear = 2026)

        assertEquals("Contador", e.title)
        assertEquals("empresa", e.category)
        assertEquals("Mensal", e.period)
        assertEquals(ExpenseType.RECURRING_FIXED, e.type)
        // February comes from the legacy `fev` key.
        assertEquals(390.0, e.amount(Month.FEB))
        assertTrue(e.isPaid(Month.FEB))
        assertFalse(e.isPaid(Month.AUG))
    }

    @Test
    fun round_trip_preserves_unknown_keys_and_body_and_migrates_feb() {
        val e = ExpenseMapper.read("Contador", "Nubank", content, fallbackYear = 2026)
        val out = ExpenseMapper.write(content, e, e)

        // Unknown keys survive verbatim.
        assertTrue(out.contains("billing_cycle: monthly"), out)
        assertTrue(out.contains("renewal_date: 2026-05-04"), out)
        // Block-list tags survive.
        assertTrue(out.contains("tags:\n  - subscription"), out)
        // Body (with wikilink) survives.
        assertTrue(out.contains("Anotações sobre o [[Contador]]."), out)
        // Legacy `fev` migrated to `feb`, value intact; no `fev` key remains.
        assertTrue(out.contains("feb: 390"), out)
        assertTrue(out.contains("feb-paid: true"), out)
        assertFalse(Regex("(?m)^fev:").containsMatchIn(out), out)
        assertFalse(Regex("(?m)^fev-paid:").containsMatchIn(out), out)
        // Explicit type is now recorded.
        assertTrue(out.contains("type: recurring-fixed"), out)
    }

    @Test
    fun changing_one_month_leaves_the_others_untouched() {
        val e = ExpenseMapper.read("Contador", "Nubank", content, fallbackYear = 2026)
        val edited = e.copy(amounts = e.amounts.toMutableMap().apply { put(Month.JUL, 400.0) })

        val out = ExpenseMapper.write(content, e, edited)

        assertTrue(out.contains("jul: 400"), out)
        // A different month keeps its original text exactly.
        assertTrue(out.contains("mar: 390"), out)
        // Reading it back reflects the change.
        val reread = ExpenseMapper.read("Contador", "Nubank", out, fallbackYear = 2026)
        assertEquals(400.0, reread.amount(Month.JUL))
        assertEquals(390.0, reread.amount(Month.MAR))
    }
}
