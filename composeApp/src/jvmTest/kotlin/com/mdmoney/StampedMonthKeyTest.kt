package com.mdmoney

import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.Month
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import com.mdmoney.ui.monthLines
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression for the account that lost every month but one.
 *
 * An Obsidian template applied over the `Regions/` folder stamped `month: July` — plus a checklist
 * body — onto all 32 expense notes in it. The vault reader recognised a ledger by that key alone, so
 * each yearly plan was read as a July ledger whose total came from a table that isn't there: R$ 0 in
 * July, nothing in the other eleven months. Opening the account in August showed an empty screen.
 *
 * The account must read as an account again, while the folder's real ledgers keep working.
 */
class StampedMonthKeyTest {

    private val vault = Files.createTempDirectory("mdmoney-stamped").toFile()
    private val account = "Regions"

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    /** An expense note as the template left it: twelve months, plus the stray keys and body. */
    private val stampedBill = """
        ---
        title: Internet
        category: "[[casa|Casa]]"
        account: "[[Regions|Regions]]"
        year: 2026
        type: recurring-variable
        subStatus: Active
        jul: 84.94
        jul-paid: true
        aug: 90
        aug-paid: false
        month: July
        created: 2026-07-27
        ---
        # 2026 - July
        - [ ] Pagar Condomínio
    """.trimIndent() + "\n"

    /** A real ledger from the same folder: month, total, table — and no monthly keys. */
    private val realLedger = """
        ---
        title: Alimentação
        category: "[[food|Alimentação]]"
        account: "[[Regions|Regions]]"
        year: 2026
        month: July
        total: 10.23
        ---

        | Date     | Note      | Amount |
        | -------- | --------- | ------ |
        | 20260715 | Starbucks | 10.23  |
    """.trimIndent() + "\n"

    @Test
    fun a_stamped_bill_keeps_its_twelve_months_and_the_folders_ledgers_still_work() = runBlocking {
        File(vault, account).mkdirs()
        File(vault, "$account/Internet - 2026.md").writeText(stampedBill)
        File(vault, "$account/2026 Jul - Alimentação.md").writeText(realLedger)

        val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))
        val expenses = repo.loadExpenses(account, 2026)
        assertEquals(2, expenses.size)

        val bill = expenses.single { it.id == "Internet - 2026" }
        assertEquals(false, bill.ledger, "monthly amounts make it an expense, not a ledger")
        assertEquals(84.94, bill.amount(Month.JUL))
        assertEquals(90.0, bill.amount(Month.AUG))

        val ledger = expenses.single { it.id == "2026 Jul - Alimentação" }
        assertTrue(ledger.ledger, "a note with a table and no monthly keys is still a ledger")
        assertEquals(10.23, ledger.amount(Month.JUL))

        // August: the bill is listed as the recurring line it is; the July ledger stays in July.
        val august = monthLines(expenses, 2026, Month.AUG)
        assertEquals(listOf("Internet"), august.recurring.map { it.title })
        assertEquals(emptyList(), august.eventual.map { it.title })

        val july = monthLines(expenses, 2026, Month.JUL)
        assertEquals(listOf("Internet"), july.recurring.map { it.title })
        assertEquals(listOf("Alimentação"), july.eventual.map { it.title })
    }
}
