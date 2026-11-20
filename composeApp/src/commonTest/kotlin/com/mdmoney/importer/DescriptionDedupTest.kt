package com.mdmoney.importer

import com.mdmoney.domain.LedgerEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The same statement line arrives worded differently depending on its source: a PDF note is cut at
 * the first line of the description and has no separator after the verb, an OFX one is complete and
 * separates every field with " - ". Re-importing the month from the other source must still see the
 * line as already there — it once doubled a whole month of Nubank.
 */
class DescriptionDedupTest {

    private fun key(date: String, note: String, amount: Double) = EntryKey.of(LedgerEntry(date, note, amount))

    private fun present(vault: List<EntryKey>, candidates: List<EntryKey>) =
        ImportDedup.alreadyPresent(candidates, ImportDedup.count(vault.map { LedgerEntry(it.date, it.note, it.cents / 100.0) }))

    @Test
    fun ofx_wording_matches_the_pdf_note_it_was_cut_from() {
        val vault = listOf(
            key("20261002", "Transferência enviada pelo Pix COLLEGATO ASSESSORIA EMPRESARIAL LTDA", 390.0),
            key("20261006", "Pagamento de boleto efetuado UNIMED CURITIBA SOCIEDADE COOPERATIVA", 1897.24),
            key("20261021", "Compra no débito GARAGEGR", 16.0),
            key("20261013", "Transferência Recebida Hananias Espindola - •••.064.389-•• - NU", 200.0),
            key("20261013", "Transferência recebida pelo Pix MARCOS NELSON HRUBA DE MOURA - 26.639.599", 554.0),
        )
        val ofx = listOf(
            key("20261002", "Transferência enviada pelo Pix - COLLEGATO ASSESSORIA EMPRESARIAL LTDA - 31.189.075/0001-96 - CORA SCFI (0403) Agência: 1 Conta: 1279237-1", 390.0),
            key("20261006", "Pagamento de boleto efetuado - UNIMED CURITIBA SOCIEDADE COOPERATIVA", 1897.24),
            key("20261021", "Compra no débito - GARAGEGR", 16.0),
            key("20261013", "Transferência Recebida - Hananias Espindola - •••.064.389-•• - NU PAGAMENTOS - IP (0260) Agência: 1 Conta: 37106444-9", 200.0),
            key("20261013", "Transferência recebida pelo Pix - MARCOS NELSON HRUBA DE MOURA - 26.639.599/0001-00 - BCO C6 S.A. (0336) Agência: 1 Conta: 13746335-9", 554.0),
        )
        assertEquals(List(5) { true }, present(vault, ofx))
    }

    @Test
    fun the_other_direction_works_too() {
        val vault = listOf(key("20261021", "Compra no débito - GARAGEGR", 16.0))
        assertEquals(listOf(true), present(vault, listOf(key("20261021", "Compra no débito GARAGEGR", 16.0))))
    }

    @Test
    fun a_different_payee_day_or_amount_is_still_new() {
        val vault = listOf(key("20261002", "Transferência enviada pelo Pix COLLEGATO ASSESSORIA EMPRESARIAL LTDA", 390.0))
        val present = present(
            vault,
            listOf(
                key("20261002", "Transferência enviada pelo Pix - RECEITA FEDERAL - 00.394.460/0058-87", 390.0), // another payee
                key("20261003", "Transferência enviada pelo Pix - COLLEGATO ASSESSORIA EMPRESARIAL LTDA", 390.0), // another day
                key("20261002", "Transferência enviada pelo Pix - COLLEGATO ASSESSORIA EMPRESARIAL LTDA", 391.0), // another amount
            ),
        )
        assertEquals(listOf(false, false, false), present)
    }

    @Test
    fun each_vault_row_covers_one_line_only() {
        // Two honest identical tolls in the statement, one in the vault: the second is new.
        val vault = listOf(key("20261016", "Compra no débito POSTO LINHA VERDE LTDA", 34.1))
        val twice = listOf(
            key("20261016", "Compra no débito - POSTO LINHA VERDE LTDA", 34.1),
            key("20261016", "Compra no débito - POSTO LINHA VERDE LTDA", 34.1),
        )
        assertEquals(listOf(true, false), present(vault, twice))
    }

    @Test
    fun transfer_rows_get_the_same_tolerance() {
        val written = key("20261017", "Pagamento de fatura - Nu Ultravioleta", 10786.7)
        val result = ImportDedup.alreadyPresent(
            candidates = listOf(key("20261017", "Pagamento de fatura", 10786.7)),
            incomes = listOf(false),
            existing = mapOf(written to 1),
            transfers = listOf(TransferEntry(written, income = false)),
        )
        assertEquals(listOf(true), result)
    }
}
