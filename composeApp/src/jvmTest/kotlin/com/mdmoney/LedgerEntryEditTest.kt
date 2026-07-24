package com.mdmoney

import com.mdmoney.data.AppSettings
import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import com.mdmoney.platform.currentYear
import com.mdmoney.ui.AppModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Editing and removing one-off purchases from inside a group's ledger, driven through [AppModel] over
 * a real vault. Editing a row must rewrite that row (and the recomputed total) in the note while
 * leaving the others alone; removing must drop it and re-sum.
 */
class LedgerEntryEditTest {

    private val account = "nubank"
    private val vault = Files.createTempDirectory("mdmoney-ledger-edit").toFile()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val settings = object : AppSettings {
        override fun language(): String? = "en"
        override fun setLanguage(code: String?) {}
        override fun decimalSeparator(): String? = null
        override fun setDecimalSeparator(code: String?) {}
    }

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    private fun openLedgerModel(): Pair<AppModel, com.mdmoney.domain.Ledger> = runBlocking {
        val year = currentYear()
        val storage = JvmVaultStorage(JvmPrefs(), vault)
        VaultRepository(storage).also {
            File(vault, account).mkdirs()
            it.addOneOff(account, year, Month.JUL, "Alimentação", "food", LedgerEntry("${year}0705", "Coffee", 10.0))
            it.addOneOff(account, year, Month.JUL, "Alimentação", "food", LedgerEntry("${year}0710", "Lunch", 20.0))
        }
        val model = AppModel(storage, settings, scope, dbPath = ":memory:")
        model.openAccount(account).join()
        val expense = model.state.value.expenses.first { it.ledger }
        model.openLedger(expense).join()
        val ledger = model.state.value.ledger
        assertNotNull(ledger, "the seeded group must open")
        model to ledger
    }

    @Test
    fun editing_an_entry_rewrites_that_row_and_the_total() = runBlocking<Unit> {
        val (model, ledger) = openLedgerModel()
        assertEquals(30.0, ledger.total, 0.001)

        val coffee = ledger.entries.first { it.note == "Coffee" }
        model.updateLedgerEntry(coffee, coffee.copy(note = "Coffee XL", amount = 15.0)).join()

        val after = model.state.value.ledger!!
        assertEquals(35.0, after.total, 0.001, "total follows the edit (15 + 20)")
        assertTrue(after.entries.any { it.note == "Coffee XL" && it.amount == 15.0 }, "the row is rewritten")
        assertFalse(after.entries.any { it.note == "Coffee" && it.amount == 10.0 }, "the old row is gone")
        // Lunch is untouched.
        assertTrue(after.entries.any { it.note == "Lunch" && it.amount == 20.0 })

        // Persisted to the note: new amount and recomputed total, storage format (dot, no symbol).
        val raw = File(File(vault, account), "${after.id}.md").readText()
        assertTrue(raw.contains("total: 35"), raw)
        // The row now reads the edited note and amount (same date, since it's the same purchase).
        assertTrue(Regex("""\|\s*${currentYear()}0705\s*\|\s*Coffee XL\s*\|\s*15\s*\|""").containsMatchIn(raw), raw)
    }

    @Test
    fun removing_an_entry_drops_it_and_re_sums() = runBlocking<Unit> {
        val (model, ledger) = openLedgerModel()
        val lunch = ledger.entries.first { it.note == "Lunch" }
        model.removeLedgerEntry(lunch).join()

        val after = model.state.value.ledger!!
        assertEquals(10.0, after.total, 0.001)
        assertFalse(after.entries.any { it.note == "Lunch" })

        val raw = File(File(vault, account), "${after.id}.md").readText()
        assertTrue(raw.contains("total: 10"), raw)
        assertFalse(raw.contains("Lunch"), raw)
    }
}
