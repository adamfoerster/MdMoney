package com.mdmoney

import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.Expense
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.MergeMode
import com.mdmoney.domain.Month
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Moving a purchase of a one-off group into a recurring expense's month, over a real (temporary) vault. */
class EntryMoveToRecurringTest {

    private val account = "casa"
    private val vault = Files.createTempDirectory("mdmoney-move-recurring").toFile().also { File(it, account).mkdirs() }
    private val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    private fun file(name: String) = File(File(vault, account), name)

    /** A recurring note with a July value, a key this app doesn't know, and prose of the user's own. */
    private fun luz(julValue: String = "jul: 55\njul-paid: false\n") {
        file("Luz.md").writeText(
            "---\ntitle: Luz\nyear: 2026\ntype: recurring-variable\nsubStatus: Active\nsource: conta de luz\n$julValue---\nLembrar de conferir a leitura.\n",
        )
    }

    private suspend fun group() = repo.addOneOff(account, 2026, Month.JUL, "Electricity", null, LedgerEntry("20260714", "FPL", 62.72))
        .let { repo.addOneOff(account, 2026, Month.JUL, "Electricity", null, LedgerEntry("20260720", "Extra", 10.0)) }

    private suspend fun luzExpense(): Expense = repo.loadExpenses(account, 2026).single { it.id == "Luz" }

    @Test
    fun replace_makes_the_entry_the_months_value_and_marks_it_paid() = runBlocking {
        luz()
        val from = group()
        val fpl = from.entries.first { it.note == "FPL" }

        val (source, note) = assertNotNull(repo.moveEntryToRecurring(from, fpl, luzExpense(), MergeMode.REPLACE))

        assertEquals(62.72, note.amounts[Month.JUL])
        assertTrue(note.paid.getValue(Month.JUL))
        assertEquals(listOf("Extra"), source.entries.map { it.note })
        assertEquals(10.0, source.total, 0.001)
        val text = file("Luz.md").readText()
        assertTrue("jul: 62.72" in text && "jul-paid: true" in text, text)
        assertTrue("source: conta de luz" in text && "Lembrar de conferir a leitura." in text, "what the app doesn't know is kept: $text")
        assertTrue(Regex("""\|\s*20260714\s*\|\s*FPL\s*\|\s*62\.72\s*\|""").containsMatchIn(text), "the purchase stays in the body: $text")
        assertTrue("FPL" !in file("2026 Jul - Electricity.md").readText(), "and leaves the group")
    }

    @Test
    fun add_sums_the_entry_onto_the_months_value() = runBlocking {
        luz()
        val from = group()

        val (_, note) = assertNotNull(repo.moveEntryToRecurring(from, from.entries.first { it.note == "FPL" }, luzExpense(), MergeMode.ADD))

        assertEquals(117.72, note.amounts[Month.JUL])
        assertTrue("jul: 117.72" in file("Luz.md").readText())
    }

    @Test
    fun a_month_without_a_value_just_takes_the_entry_in_either_mode() = runBlocking {
        luz(julValue = "")
        val from = group()

        val (_, note) = assertNotNull(repo.moveEntryToRecurring(from, from.entries.first { it.note == "Extra" }, luzExpense(), MergeMode.ADD))

        assertEquals(10.0, note.amounts[Month.JUL])
        assertNull(note.amounts[Month.AUG], "other months stay as they were")
    }

    @Test
    fun the_group_empties_when_its_last_entry_moves() = runBlocking {
        luz()
        val only = repo.addOneOff(account, 2026, Month.JUL, "Solo", null, LedgerEntry("20260701", "Only", 5.0))

        val (source, _) = assertNotNull(repo.moveEntryToRecurring(only, only.entries.single(), luzExpense(), MergeMode.REPLACE))

        assertEquals(emptyList(), source.entries)
        assertNull(repo.cachedExpenses(account).single { it.id == "2026 Jul - Solo" }.amounts[Month.JUL], "an empty group carries no month value")
    }

    @Test
    fun only_a_recurring_note_of_the_groups_year_takes_an_entry() = runBlocking {
        val from = group()
        val fpl = from.entries.first { it.note == "FPL" }
        file("Property Tax.md").writeText("---\ntitle: Property Tax\nyear: 2026\ntype: eventual\njul: 1\n---\n")
        val eventual = repo.loadExpenses(account, 2026).single { it.id == "Property Tax" }
        val ledgerRow = repo.loadExpenses(account, 2026).single { it.ledger }

        assertNull(repo.moveEntryToRecurring(from, fpl, eventual, MergeMode.ADD), "a one-off note is not a recurring one")
        assertNull(repo.moveEntryToRecurring(from, fpl, ledgerRow, MergeMode.ADD), "nor is another group")
        assertEquals(2, repo.loadLedger(account, from.id, 2026)!!.entries.size, "nothing was taken out of the group")
        assertEquals("---\ntitle: Property Tax\nyear: 2026\ntype: eventual\njul: 1\n---\n", file("Property Tax.md").readText())
    }
}
