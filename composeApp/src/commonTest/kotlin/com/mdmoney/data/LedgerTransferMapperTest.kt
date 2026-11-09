package com.mdmoney.data

import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A group that is one side of a transfer says which account the other side is in. */
class LedgerTransferMapperTest {

    private val sample = """
        ---
        title: Transferência para Savings
        renewal_date: 2027-01-01
        account: "[[nubank|Nubank]]"
        year: 2026
        month: July
        total: 100
        transfer: Savings
        ---

        Prose written in Obsidian.

        | Date     | Note | Amount |
        | -------- | ---- | ------ |
        | 20260715 | rent | 100    |
    """.trimIndent() + "\n"

    @Test
    fun reads_the_other_account() {
        val ledger = LedgerMapper.read("2026 Jul - Transferência para Savings", "nubank", sample, 2026)!!
        assertEquals("Savings", ledger.transfer)
        assertFalse(ledger.income)
    }

    @Test
    fun round_trips_keeping_unknown_keys_and_prose() {
        val ledger = LedgerMapper.read("x", "nubank", sample, 2026)!!
        val again = LedgerMapper.write(sample, ledger)
        assertTrue("renewal_date: 2027-01-01" in again)
        assertTrue("Prose written in Obsidian." in again)
        assertEquals("Savings", LedgerMapper.read("x", "nubank", again, 2026)!!.transfer)
    }

    @Test
    fun an_ordinary_group_gets_no_transfer_key() {
        val plain = Ledger("2026 Jul - Food", "nubank", "Food", null, 2026, Month.JUL, listOf(LedgerEntry("20260701", "a", 1.0)))
        val text = LedgerMapper.write(null, plain)
        assertFalse("transfer" in text)
        assertNull(LedgerMapper.read("x", "nubank", text, 2026)!!.transfer)
    }

    @Test
    fun writes_the_key_for_a_transfer_group() {
        val out = Ledger("2026 Jul - T", "nubank", "T", null, 2026, Month.JUL, listOf(LedgerEntry("20260701", "a", 1.0)), transfer = "Savings")
        assertTrue("transfer: Savings" in LedgerMapper.write(null, out))
    }
}
