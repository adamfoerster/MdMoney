package com.mdmoney

import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.Month
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A transfer leaves one account and arrives in the other, as plain ledger notes. */
class TransferTest {

    private val vault = Files.createTempDirectory("mdmoney-transfer").toFile()
    private val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    private fun accounts() = listOf("contaA", "contaB").onEach { java.io.File(vault, it).mkdirs() }

    @Test
    fun negative_amount_leaves_the_source_and_arrives_in_the_destination() = runBlocking<Unit> {
        accounts()
        val (out, incoming) = assertNotNull(
            repo.addTransfer("contaA", "contaB", 2026, Month.JUL, "To B", "From A", "20260715", "rent", -100.0),
        )
        assertEquals(100.0, out.total)
        assertTrue(!out.income, "the source side is spending")
        assertEquals(100.0, incoming.total, "the destination receives the absolute value")
        assertTrue(incoming.income, "the destination side is income")

        // Re-read from disk: balances come from the files, not from what the call returned.
        val a = repo.loadExpenses("contaA", 2026).single()
        val b = repo.loadExpenses("contaB", 2026).single()
        assertEquals(100.0, a.amount(Month.JUL))
        assertTrue(!a.isIncome && a.isPaid(Month.JUL))
        assertEquals(100.0, b.amount(Month.JUL))
        assertTrue(b.isIncome && b.isPaid(Month.JUL))
    }

    @Test
    fun positive_amount_moves_the_same_way_and_adds_to_an_existing_group() = runBlocking<Unit> {
        accounts()
        repo.addTransfer("contaA", "contaB", 2026, Month.JUL, "To B", "From A", "20260701", "x", 40.0)
        val (out, incoming) = assertNotNull(
            repo.addTransfer("contaA", "contaB", 2026, Month.JUL, "To B", "From A", "20260702", "y", 25.5),
        )
        assertEquals(65.5, out.total)
        assertEquals(65.5, incoming.total)
        assertEquals(2, out.entries.size)
    }

    @Test
    fun same_account_or_zero_writes_nothing() = runBlocking<Unit> {
        accounts()
        assertNull(repo.addTransfer("contaA", "contaA", 2026, Month.JUL, "t", "f", "20260701", "", 10.0))
        assertNull(repo.addTransfer("contaA", "contaB", 2026, Month.JUL, "t", "f", "20260701", "", 0.0))
        assertTrue(repo.loadExpenses("contaA", 2026).isEmpty())
        assertTrue(repo.loadExpenses("contaB", 2026).isEmpty())
    }
}
