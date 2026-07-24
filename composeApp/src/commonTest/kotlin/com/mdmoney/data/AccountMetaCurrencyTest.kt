package com.mdmoney.data

import com.mdmoney.domain.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The account root note (`<Account>.md`) gained a `currency:` key. Like every vault-format change it
 * must round-trip: reading a preset, writing one back, and — the core promise — leaving unknown keys
 * and body prose exactly as the user (or Obsidian) left them.
 */
class AccountMetaCurrencyTest {

    private val content = buildString {
        appendLine("---")
        appendLine("type: account")
        appendLine("account: nubank")
        appendLine("title: Nubank")
        appendLine("currency: EUR")
        appendLine("color: \"#8a1f1f\"") // an unknown key the app doesn't model
        appendLine("initial-2026: 1200")
        appendLine("---")
        append("\nNotas sobre a conta [[Nubank]].\n")
    }

    @Test
    fun reads_a_preset_currency() {
        val meta = AccountMeta.read("nubank", content)
        assertEquals(Currency.EUR, meta.currency)
        assertEquals("€", meta.currency.symbol)
        // The rest of the note still reads.
        assertEquals("Nubank", meta.title)
        assertEquals(1200.0, meta.initialBalance(2026))
    }

    @Test
    fun writing_a_currency_preserves_unknown_keys_body_and_balance() {
        val out = AccountMeta.read("nubank", content).withCurrency(Currency.USD).serialize()

        assertTrue(out.contains("currency: USD"), out)
        assertFalse(out.contains("currency: EUR"), out)
        // Unknown key and body survive verbatim; the balance is untouched.
        assertTrue(out.contains("color: \"#8a1f1f\""), out)
        assertTrue(out.contains("Notas sobre a conta [[Nubank]]."), out)
        assertTrue(out.contains("initial-2026: 1200"), out)

        val reread = AccountMeta.read("nubank", out)
        assertEquals(Currency.USD, reread.currency)
        assertEquals(1200.0, reread.initialBalance(2026))
    }

    @Test
    fun a_custom_symbol_round_trips_as_typed() {
        val out = AccountMeta.read("nubank", content).withCurrency(Currency.custom("kr")).serialize()
        assertTrue(out.contains("currency: kr"), out)

        val reread = AccountMeta.read("nubank", out)
        assertTrue(reread.currency.isCustom)
        assertEquals("kr", reread.currency.symbol)
    }

    @Test
    fun clearing_to_none_removes_the_key_but_keeps_the_note() {
        val out = AccountMeta.read("nubank", content).withCurrency(Currency.NONE).serialize()

        assertFalse(Regex("(?m)^currency:").containsMatchIn(out), out)
        // Everything else is still there — clearing the currency is not clearing the note.
        assertTrue(out.contains("initial-2026: 1200"), out)
        assertTrue(out.contains("color: \"#8a1f1f\""), out)
        assertEquals(Currency.NONE, AccountMeta.read("nubank", out).currency)
    }

    @Test
    fun editing_title_and_currency_together_round_trips_and_keeps_the_rest() {
        // What repo.updateAccount does: withTitle().withCurrency() then one write.
        val out = AccountMeta.read("nubank", content)
            .withTitle("Nubank Roxinho")
            .withCurrency(Currency.custom("kr"))
            .serialize()

        assertTrue(out.contains("title: Nubank Roxinho"), out)
        assertTrue(out.contains("currency: kr"), out)
        // Untouched: the unknown key, the body, and the opening balance.
        assertTrue(out.contains("color: \"#8a1f1f\""), out)
        assertTrue(out.contains("Notas sobre a conta [[Nubank]]."), out)
        assertTrue(out.contains("initial-2026: 1200"), out)

        val reread = AccountMeta.read("nubank", out)
        assertEquals("Nubank Roxinho", reread.title)
        assertEquals("kr", reread.currency.symbol)
        assertEquals(1200.0, reread.initialBalance(2026))
    }

    @Test
    fun a_note_with_no_currency_key_reads_as_none() {
        val bare = "---\ntype: account\naccount: nubank\ntitle: Nubank\ninitial-2026: 500\n---\n"
        assertEquals(Currency.NONE, AccountMeta.read("nubank", bare).currency)
    }
}
