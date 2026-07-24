package com.mdmoney

import com.mdmoney.data.AppSettings
import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import com.mdmoney.platform.currentMonth
import com.mdmoney.platform.currentYear
import com.mdmoney.ui.AppModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [AppModel] over a real vault to cover the add-one-off-to-a-group flow.
 *
 * Regression for: the "add entry" button on a group's ledger opened the one-off sheet without
 * dismissing the ledger, so both modal sheets stacked and the ledger — composed last in `App.kt` —
 * hid the one-off sheet entirely. From the user's seat there was no way to record a purchase into a
 * group. The sheet must replace the ledger, and saving must return to the ledger carrying the row.
 */
class OneOffFlowTest {

    private val account = "nubank"
    private val vault = Files.createTempDirectory("mdmoney-oneoff").toFile()
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

    @Test
    fun add_entry_from_a_ledger_replaces_it_then_returns_with_the_new_row() = runBlocking<Unit> {
        val year = currentYear()
        // Seed the group in a month that is *not* the one Home defaults to, so honouring the ledger's
        // own month (rather than Home's) is actually exercised.
        val ledgerMonth = Month.ALL.first { it.number != currentMonth() }
        val storage = JvmVaultStorage(JvmPrefs(), vault)
        VaultRepository(storage).also {
            java.io.File(vault, account).mkdirs()
            it.addOneOff(account, year, ledgerMonth, "Alimentação", "food", LedgerEntry("${year}0101", "Starbucks", 11.23))
        }

        val model = AppModel(storage, settings, scope, dbPath = ":memory:")
        model.openAccount(account).join()

        val ledgerExpense = model.state.value.expenses.firstOrNull { it.ledger }
        assertNotNull(ledgerExpense, "the seeded group must load as a ledger expense")
        model.openLedger(ledgerExpense).join()
        assertNotNull(model.state.value.ledger, "opening the group must show its ledger")

        // Tapping "add entry" on the ledger.
        model.openAddOneOff(ledgerExpense.title, ledgerMonth, reopenLedger = true)

        val oneOff = model.state.value.oneOff
        assertNotNull(oneOff, "the one-off sheet must be shown")
        assertNull(model.state.value.ledger, "the ledger must be dismissed so the sheets don't stack")
        assertEquals(ledgerMonth, oneOff.month, "the new purchase defaults to the ledger's month, not Home's")
        assertTrue(oneOff.reopenLedger)

        // Recording the purchase.
        model.saveOneOff(ledgerExpense.title, "food", "${year}0115", "Padaria", 4.0).join()

        assertNull(model.state.value.oneOff, "the sheet closes after saving")
        val reopened = model.state.value.ledger
        assertNotNull(reopened, "saving from a ledger returns to that ledger")
        assertEquals(2, reopened.entries.size, "the ledger now holds both purchases")
        assertTrue(reopened.entries.any { it.note == "Padaria" && it.amount == 4.0 }, "the new row landed in it")
    }
}
