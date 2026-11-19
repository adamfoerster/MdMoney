package com.mdmoney

import com.mdmoney.data.MatchPatterns
import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.Category
import com.mdmoney.domain.ExpenseType
import com.mdmoney.importer.AccountGroups
import com.mdmoney.importer.Direction
import com.mdmoney.importer.GroupChoice
import com.mdmoney.importer.ImportPlanner
import com.mdmoney.importer.SimpleDate
import com.mdmoney.importer.StatementTransaction
import com.mdmoney.importer.TxKind
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A statement teaches the notes it lands in; the next statement is filed by what they learned. */
class PatternLearningFlowTest {

    private val account = "Regions"
    private val vault = Files.createTempDirectory("mdmoney-patterns").toFile().also { File(it, account).mkdirs() }
    private val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))
    private val categories = listOf(Category("groceries", "Groceries"))

    // A recurring bill with a note of the user's own around it, and no `match:` yet.
    private val internet = "---\ntitle: Internet\naccount: \"[[Regions|Regions]]\"\nyear: 2026\ntype: recurring-variable\n" +
        com.mdmoney.domain.Month.ALL.joinToString("") { "${it.key}:\n${it.paidKey}: false\n" } + "---\n\nFibra.\n"

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    private fun tx(month: Int, day: Int, desc: String, amount: Double) =
        StatementTransaction(SimpleDate(2026, month, day), desc, amount, Direction.DEBIT, TxKind.PURCHASE, null, null)

    private suspend fun groups() = AccountGroups(repo.loadExpenses(account, 2026))

    private fun file(name: String) = File(vault, "$account/$name.md")

    @Test
    fun the_second_statement_is_filed_by_what_the_first_one_taught() = runBlocking {
        file("Internet - 2026").writeText(internet)

        // September: nothing is known, so the user files both lines by hand.
        val sep = ImportPlanner.initialRows(
            listOf(
                tx(9, 8, "Card Purchase Publix #1899 5411 Palm Bay FL 32907 3914", 55.54),
                tx(9, 22, "Recurring Card Transaction Blue Stream 4899 954-753-0100 FL 33065 3914", 84.94),
            ),
            emptyMap(),
            groups = groups(),
        )
        assertTrue(sep.all { it.group == null }, "no note has a pattern yet")
        val sepFiled = listOf(
            sep[0].copy(selected = true, category = "groceries"),
            sep[1].copy(selected = true, entryType = ExpenseType.RECURRING_VARIABLE, group = GroupChoice("Internet", "Internet - 2026")),
        )
        repo.importEntries(account, ImportPlanner.entries(sepFiled, categories, "Imported", "Received", account, groups = groups()))

        assertEquals(listOf("Card Purchase Publix*"), MatchPatterns.read(file("2026 Sep - Groceries").readText()).map { it.text })
        val bill = file("Internet - 2026").readText()
        assertEquals(listOf("Recurring Card Transaction Blue Stream*"), MatchPatterns.read(bill).map { it.text })
        assertTrue(bill.contains("sep: 84.94") && bill.endsWith("Fibra.\n\n| Date     | Note                                                                   | Amount |\n" +
            "| -------- | ---------------------------------------------------------------------- | ------ |\n" +
            "| 20260922 | Recurring Card Transaction Blue Stream 4899 954-753-0100 FL 33065 3914 | 84.94  |\n"), bill)

        // October: both lines arrive already filed, from the files alone.
        val oct = ImportPlanner.initialRows(
            listOf(
                tx(10, 3, "Card Purchase Publix #1899 5411 Palm Bay FL 32907 3914", 61.20),
                tx(10, 22, "Recurring Card Transaction Blue Stream 4899 954-753-0100 FL 33065 3914", 84.94),
            ),
            emptyMap(),
            groups = groups(),
        )
        assertEquals(GroupChoice("Groceries"), oct[0].group)
        assertEquals("groceries", oct[0].category)
        assertEquals(GroupChoice("Internet", "Internet - 2026"), oct[1].group)
        assertEquals(ExpenseType.RECURRING_VARIABLE, oct[1].entryType)

        val result = repo.importEntries(account, ImportPlanner.entries(oct.map { it.copy(selected = true) }, categories, "Imported", "Received", account, groups = groups()))
        assertEquals(2, result.added)
        val octGroceries = file("2026 Oct - Groceries").readText()
        assertTrue(octGroceries.contains("total: 61.2") && octGroceries.contains("category: \"[[groceries|Groceries]]\""), octGroceries)
        assertEquals(listOf("Card Purchase Publix*"), MatchPatterns.read(octGroceries).map { it.text }, "the new month's note carries it too")
        val billAfter = file("Internet - 2026").readText()
        assertEquals(listOf("Recurring Card Transaction Blue Stream*"), MatchPatterns.read(billAfter).map { it.text }, "not learned twice")
        assertTrue(billAfter.contains("oct: 84.94") && billAfter.contains("oct-paid: true"), billAfter)
    }
}
