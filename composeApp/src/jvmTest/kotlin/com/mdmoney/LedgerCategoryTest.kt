package com.mdmoney

import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Changing the category of a one-off group from its detail, over a real (temporary) vault. */
class LedgerCategoryTest {

    private val account = "casa"
    private val vault = Files.createTempDirectory("mdmoney-ledger-category").toFile().also { File(it, account).mkdirs() }
    private val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    private fun file(name: String) = File(File(vault, account), name)

    @Test
    fun the_category_is_rewritten_and_the_rest_of_the_note_is_kept() = runBlocking {
        val ledger = repo.addOneOff(account, 2026, Month.JUL, "Electricity", "house", LedgerEntry("20260714", "FPL", 62.72))
        val note = file("2026 Jul - Electricity.md")
        note.appendText("\nMinha nota.\n")

        val saved = repo.setLedgerCategory(ledger, "utilities")

        assertEquals("utilities", saved.category)
        val text = note.readText()
        assertTrue("utilities" in text && "house" !in text, text)
        assertTrue("Minha nota." in text && "FPL" in text, "prose and rows survive: $text")
        assertEquals("utilities", repo.loadLedger(account, ledger.id, 2026)!!.category)
        assertTrue(file("categories/utilities.md").exists() || File(vault, "categories/utilities.md").exists(), "a new category gets its note")
    }

    @Test
    fun no_category_clears_it() = runBlocking {
        val ledger = repo.addOneOff(account, 2026, Month.JUL, "Electricity", "house", LedgerEntry("20260714", "FPL", 62.72))

        repo.setLedgerCategory(ledger, null)

        assertNull(repo.loadLedger(account, ledger.id, 2026)!!.category)
        assertFalse("house" in file("2026 Jul - Electricity.md").readText())
    }

    @Test
    fun renaming_moves_the_note_and_keeps_everything_in_it() = runBlocking {
        val ledger = repo.addOneOff(account, 2026, Month.JUL, "Electricity", "house", LedgerEntry("20260714", "FPL", 62.72))
        file("2026 Jul - Electricity.md").appendText("\nMinha nota.\n")

        val renamed = repo.renameLedger(ledger, "Energia")!!

        assertEquals("2026 Jul - Energia", renamed.id)
        assertFalse(file("2026 Jul - Electricity.md").exists(), "the old note is gone")
        val text = file("2026 Jul - Energia.md").readText()
        assertTrue("title: Energia" in text && "Minha nota." in text && "FPL" in text && "house" in text, text)
        assertEquals(listOf("2026 Jul - Energia"), repo.cachedExpenses(account).map { it.id })
    }

    @Test
    fun a_name_another_group_already_has_is_refused_and_nothing_changes() = runBlocking {
        val ledger = repo.addOneOff(account, 2026, Month.JUL, "Electricity", null, LedgerEntry("20260714", "FPL", 62.72))
        repo.addOneOff(account, 2026, Month.JUL, "Energia", null, LedgerEntry("20260701", "Other", 1.0))

        assertNull(repo.renameLedger(ledger, "energia"))
        assertNull(repo.renameLedger(ledger, "  "))
        assertTrue(file("2026 Jul - Electricity.md").exists() && "Other" !in file("2026 Jul - Electricity.md").readText())
    }

    @Test
    fun a_case_only_rename_keeps_the_note() = runBlocking {
        val ledger = repo.addOneOff(account, 2026, Month.JUL, "energia", null, LedgerEntry("20260714", "FPL", 62.72))

        val renamed = repo.renameLedger(ledger, "Energia")!!

        assertEquals("Energia", renamed.title)
        assertTrue("title: Energia" in File(File(vault, account), renamed.id + ".md").readText())
        assertEquals(1, File(vault, account).listFiles()!!.count { it.name.endsWith(".md") })
    }
}
