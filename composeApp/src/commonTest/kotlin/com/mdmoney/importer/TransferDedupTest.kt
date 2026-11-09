package com.mdmoney.importer

import com.mdmoney.domain.LedgerEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferDedupTest {

    private fun key(date: String, note: String, amount: Double) = EntryKey.of(LedgerEntry(date, note, amount))

    @Test
    fun other_side_of_a_transfer_is_recognised_whatever_the_note_says() {
        val written = key("20260715", "Transfer from Nubank", 100.0)
        val present = ImportDedup.alreadyPresent(
            candidates = listOf(key("20260715", "PIX RECEBIDO JOAO", 100.0)),
            incomes = listOf(true),
            existing = mapOf(written to 1),
            transfers = listOf(TransferEntry(written, income = true)),
        )
        assertEquals(listOf(true), present)
    }

    @Test
    fun direction_day_and_amount_must_all_agree() {
        val written = key("20260715", "x", 100.0)
        val transfers = listOf(TransferEntry(written, income = true))
        val present = ImportDedup.alreadyPresent(
            candidates = listOf(
                key("20260715", "a", 100.0), // a debit, not the credit that was written
                key("20260716", "b", 100.0), // another day
                key("20260715", "c", 100.5), // another amount
            ),
            incomes = listOf(false, true, true),
            existing = mapOf(written to 1),
            transfers = transfers,
        )
        assertEquals(listOf(false, false, false), present)
    }

    @Test
    fun one_vault_row_covers_one_statement_line() {
        val written = key("20260715", "x", 100.0)
        val present = ImportDedup.alreadyPresent(
            candidates = listOf(key("20260715", "a", 100.0), key("20260715", "b", 100.0)),
            incomes = listOf(true, true),
            existing = mapOf(written to 1),
            transfers = listOf(TransferEntry(written, income = true)),
        )
        assertEquals(listOf(true, false), present)
    }

    @Test
    fun a_repeated_line_with_the_same_note_does_not_eat_a_second_transfer_row() {
        // Two real $10 credits on one day; the vault has one of them, written as a transfer.
        val written = key("20260715", "x", 10.0)
        val present = ImportDedup.alreadyPresent(
            candidates = listOf(key("20260715", "x", 10.0), key("20260715", "x", 10.0)),
            incomes = listOf(true, true),
            existing = mapOf(written to 1),
            transfers = listOf(TransferEntry(written, income = true)),
        )
        assertEquals(listOf(true, false), present)
    }
}
