package com.mdmoney

import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.importer.Direction
import com.mdmoney.importer.EntryKey
import com.mdmoney.importer.ImportEntry
import com.mdmoney.importer.ImportPlanner
import com.mdmoney.importer.SimpleDate
import com.mdmoney.importer.StatementTransaction
import com.mdmoney.importer.TxKind
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Statement lines filed as transfers reach the other account once, and are not imported twice. */
class ImportTransferTest {

    private val vault = Files.createTempDirectory("mdmoney-import-transfer").toFile()
    private val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))
    private val jul = setOf(2026 to Month.JUL)

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    private fun accounts() = listOf("A", "B").onEach { java.io.File(vault, it).mkdirs() }

    private fun out(note: String, amount: Double = 100.0, day: String = "20260715") = ImportEntry(
        year = 2026, month = Month.JUL, title = "Transfer to B", category = null, income = false,
        entry = LedgerEntry(day, note, amount), counterAccount = "B", counterTitle = "Transfer from A",
    )

    private suspend fun amountsIn(account: String) = repo.loadExpenses(account, 2026).map { it.isIncome to it.amount(Month.JUL) }

    @Test
    fun a_transfer_line_is_spent_here_and_received_there() = runBlocking<Unit> {
        accounts()
        val result = repo.importEntries("A", listOf(out("PIX ENVIADO")))
        assertEquals(1, result.added)
        assertEquals(listOf(false to 100.0), amountsIn("A"))
        assertEquals(listOf(true to 100.0), amountsIn("B"))
    }

    @Test
    fun the_other_accounts_statement_line_is_recognised_as_already_imported() = runBlocking<Unit> {
        accounts()
        repo.importEntries("A", listOf(out("PIX ENVIADO")))

        // B's statement describes the same money in its own words.
        val credit = StatementTransaction(SimpleDate(2026, 7, 15), "PIX RECEBIDO ADAM", 100.0, Direction.CREDIT, TxKind.INCOME, null)
        val other = StatementTransaction(SimpleDate(2026, 7, 16), "Salary", 100.0, Direction.CREDIT, TxKind.INCOME, null)
        val rows = ImportPlanner.initialRows(
            listOf(credit, other),
            repo.existingEntryCounts("B", jul),
            repo.existingTransfers("B", jul),
        )
        assertTrue(rows[0].alreadyImported, "the mirrored credit must not come in a second time")
        assertTrue(!rows[0].selected)
        assertTrue(!rows[1].alreadyImported, "a credit on another day is a different line")
    }

    @Test
    fun without_the_transfer_marker_the_notes_differ_and_nothing_is_recognised() = runBlocking<Unit> {
        accounts()
        repo.addOneOff("B", 2026, Month.JUL, "Misc", null, LedgerEntry("20260715", "something", 100.0))
        val debit = StatementTransaction(SimpleDate(2026, 7, 15), "PIX", 100.0, Direction.DEBIT, TxKind.PURCHASE, null)
        val rows = ImportPlanner.initialRows(listOf(debit), repo.existingEntryCounts("B", jul), repo.existingTransfers("B", jul))
        assertTrue(!rows[0].alreadyImported)
    }

    @Test
    fun importing_the_statement_again_adds_nothing_on_either_side() = runBlocking<Unit> {
        accounts()
        repo.importEntries("A", listOf(out("PIX ENVIADO")))
        val key = EntryKey.of(LedgerEntry("20260715", "PIX ENVIADO", 100.0))
        assertEquals(mapOf(key to 1), repo.existingEntryCounts("A", jul))
        val again = ImportPlanner.initialRows(
            listOf(StatementTransaction(SimpleDate(2026, 7, 15), "PIX ENVIADO", 100.0, Direction.DEBIT, TxKind.TRANSFER, null)),
            repo.existingEntryCounts("A", jul),
            repo.existingTransfers("A", jul),
        )
        assertTrue(again[0].alreadyImported)
        assertEquals(listOf(true to 100.0), amountsIn("B"))
    }

    @Test
    fun a_credit_already_in_the_other_account_is_not_written_again() = runBlocking<Unit> {
        accounts()
        // B's own statement was imported first, as an ordinary income line.
        repo.importEntries(
            "B",
            listOf(
                ImportEntry(2026, Month.JUL, "Income", null, true, LedgerEntry("20260715", "PIX RECEBIDO ADAM", 100.0)),
            ),
        )
        repo.importEntries("A", listOf(out("PIX ENVIADO")))
        assertEquals(listOf(true to 100.0), amountsIn("B"), "B must still hold the credit exactly once")
        assertEquals(listOf(false to 100.0), amountsIn("A"))
    }

    @Test
    fun two_identical_transfers_on_one_day_arrive_twice() = runBlocking<Unit> {
        accounts()
        repo.importEntries("A", listOf(out("PIX"), out("PIX")))
        assertEquals(listOf(true to 200.0), amountsIn("B"))
        assertEquals(listOf(false to 200.0), amountsIn("A"))
    }
}
