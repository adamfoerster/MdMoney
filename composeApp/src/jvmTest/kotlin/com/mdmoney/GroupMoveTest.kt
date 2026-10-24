package com.mdmoney

import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.domain.groupTitles
import com.mdmoney.importer.ImportEntry
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Moving a purchase into, out of and between groups, over a real (temporary) vault. */
class GroupMoveTest {

    private val account = "nubank"
    private val vault = Files.createTempDirectory("mdmoney-group-move").toFile().also { File(it, account).mkdirs() }
    private val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    private fun file(name: String) = File(File(vault, account), name)

    private suspend fun seeded() = repo.addOneOff(account, 2026, Month.JUL, "Alimentação", "food", LedgerEntry("20260705", "Coffee", 10.0))
        .let { repo.addOneOff(account, 2026, Month.JUL, "Alimentação", "food", LedgerEntry("20260710", "Lunch", 20.5)) }

    @Test
    fun an_entry_moves_to_another_group_and_both_totals_follow() = runBlocking {
        val from = seeded()
        val lunch = from.entries.first { it.note == "Lunch" }

        val (source, target) = assertNotNull(repo.moveEntry(from, lunch, "Restaurantes"))

        assertEquals(10.0, source.total, 0.001)
        assertEquals(20.5, target.total, 0.001)
        assertEquals("food", target.category, "a new group inherits the category")
        val old = file("2026 Jul - Alimentação.md").readText()
        assertTrue("total: 10" in old && "Lunch" !in old, old)
        val new = file("2026 Jul - Restaurantes.md").readText()
        assertTrue("total: 20.5" in new && Regex("""\|\s*20260710\s*\|\s*Lunch\s*\|\s*20\.5\s*\|""").containsMatchIn(new), new)
    }

    @Test
    fun moving_into_an_existing_group_adds_to_its_table_and_keeps_its_prose() = runBlocking {
        val from = seeded()
        repo.addOneOff(account, 2026, Month.JUL, "Restaurantes", null, LedgerEntry("20260701", "Pizza", 40.0))
        val target = file("2026 Jul - Restaurantes.md")
        target.appendText("\nMinha nota.\n")

        repo.moveEntry(from, from.entries.first { it.note == "Coffee" }, "Restaurantes")

        val text = target.readText()
        assertTrue("total: 50" in text, text)
        assertTrue("Minha nota." in text && "Pizza" in text && "Coffee" in text, text)
    }

    @Test
    fun moving_to_the_same_group_changes_nothing() = runBlocking {
        val from = seeded()
        val before = file("2026 Jul - Alimentação.md").readText()
        assertNull(repo.moveEntry(from, from.entries.first(), "Alimentação"))
        assertEquals(before, file("2026 Jul - Alimentação.md").readText())
    }

    @Test
    fun an_entry_taken_out_of_its_group_becomes_a_paid_note_of_its_own() = runBlocking {
        val from = seeded()
        val coffee = from.entries.first { it.note == "Coffee" }

        val (source, note) = repo.ungroupEntry(from, coffee)

        assertEquals(20.5, source.total, 0.001)
        assertEquals(ExpenseType.EVENTUAL, note.type)
        assertEquals(10.0, note.amounts[Month.JUL])
        assertTrue(note.paid.getValue(Month.JUL))
        val text = file("Coffee.md").readText()
        assertTrue("jul: 10" in text && "jul-paid: true" in text && "type: eventual" in text, text)
        assertTrue(Regex("""\|\s*20260705\s*\|\s*Coffee\s*\|\s*10\s*\|""").containsMatchIn(text), "the date is kept: $text")
        assertFalse("Coffee" in file("2026 Jul - Alimentação.md").readText())
    }

    @Test
    fun a_taken_out_entry_never_overwrites_a_note_with_the_same_name() = runBlocking {
        val from = seeded()
        file("Coffee.md").writeText("---\ntitle: Coffee\n---\nmine\n")

        val (_, note) = repo.ungroupEntry(from, from.entries.first { it.note == "Coffee" })

        assertEquals("Coffee 2", note.id)
        assertEquals("---\ntitle: Coffee\n---\nmine\n", file("Coffee.md").readText())
    }

    @Test
    fun a_note_taken_out_and_put_back_ends_where_it_started() = runBlocking {
        val from = seeded()
        val coffee = from.entries.first { it.note == "Coffee" }
        val (source, note) = repo.ungroupEntry(from, coffee)

        val back = assertNotNull(repo.groupExpense(note, "Alimentação"))

        assertEquals(30.5, back.total, 0.001)
        assertTrue(back.entries.contains(coffee), "same date, note and amount")
        assertFalse(file("Coffee.md").exists(), "the note is gone")
        assertEquals(source.entries.size + 1, back.entries.size)
    }

    private fun plain(name: String, extra: String = "", body: String = "") {
        file("$name.md").writeText(
            "---\ntitle: $name\nyear: 2026\ntype: eventual\nsubStatus: Active\n${extra}aug: 33.08\naug-paid: true\n---\n$body",
        )
    }

    @Test
    fun a_plain_one_off_folds_into_a_group_of_its_month() = runBlocking {
        plain("Farmácia")
        val expense = repo.loadExpenses(account, 2026).single()
        assertEquals(Month.AUG, expense.groupableMonth)

        val group = assertNotNull(repo.groupExpense(expense, "Saúde"))

        assertEquals(Month.AUG, group.month)
        assertEquals(listOf(LedgerEntry("20260801", "Farmácia", 33.08)), group.entries, "no date known: the first of the month")
        assertFalse(file("Farmácia.md").exists())
        assertTrue("total: 33.08" in file("2026 Aug - Saúde.md").readText())
    }

    @Test
    fun a_note_with_prose_or_keys_of_its_own_is_left_alone() = runBlocking {
        plain("Com texto", body = "\nLembrar de pedir a nota fiscal.\n")
        plain("Com chave", extra = "loja: Droga Raia\n")
        for (name in listOf("Com texto", "Com chave")) {
            val before = file("$name.md").readText()
            val expense = repo.loadExpenses(account, 2026).first { it.id == name }
            assertNull(repo.groupExpense(expense, "Saúde"), name)
            assertEquals(before, file("$name.md").readText(), "$name untouched")
        }
        assertFalse(file("2026 Aug - Saúde.md").exists())
    }

    @Test
    fun only_a_single_month_one_off_can_be_grouped() {
        fun expense(type: ExpenseType, vararg months: Month) = Expense.empty(account, 2026, type).copy(
            amounts = Month.ALL.associateWith { if (it in months) 10.0 else null },
        )
        assertEquals(Month.MAR, expense(ExpenseType.EVENTUAL, Month.MAR).groupableMonth)
        assertEquals(Month.MAR, expense(ExpenseType.INCOME, Month.MAR).groupableMonth)
        assertNull(expense(ExpenseType.EVENTUAL, Month.MAR, Month.APR).groupableMonth)
        assertNull(expense(ExpenseType.RECURRING_FIXED, Month.MAR).groupableMonth)
        assertNull(expense(ExpenseType.EVENTUAL).groupableMonth)
    }

    @Test
    fun groups_on_offer_are_those_of_the_month_and_kind_without_the_current_one() = runBlocking {
        seeded()
        repo.addOneOff(account, 2026, Month.JUL, "Restaurantes", null, LedgerEntry("20260701", "Pizza", 40.0))
        repo.addOneOff(account, 2026, Month.AUG, "Agosto", null, LedgerEntry("20260801", "X", 1.0))
        val expenses = repo.loadExpenses(account, 2026)

        assertEquals(listOf("Restaurantes"), groupTitles(expenses, Month.JUL, income = false, except = "Alimentação"))
        assertEquals(emptyList(), groupTitles(expenses, Month.JUL, income = true))
    }

    @Test
    fun a_statement_line_filed_under_no_group_is_its_own_note_and_is_not_imported_twice() = runBlocking {
        val line = ImportEntry(
            2026, Month.AUG, "Mercado Bella", "food", income = false,
            entry = LedgerEntry("20260825", "Mercado Bella", 166.92), standalone = true,
        )
        val first = repo.importEntries(account, listOf(line))
        assertEquals(1, first.added)
        assertEquals(1, first.notes)
        val text = file("Mercado Bella.md").readText()
        assertTrue("aug: 166.92" in text && "aug-paid: true" in text, text)
        assertFalse(vault.walkTopDown().any { it.name.startsWith("2026 Aug -") }, "no group was made")

        val again = repo.importEntries(account, listOf(line))
        assertEquals(0, again.added)
        assertEquals(1, again.skipped)
        assertEquals(text, file("Mercado Bella.md").readText())
    }
}
