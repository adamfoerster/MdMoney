package com.mdmoney.importer

import com.mdmoney.domain.Category
import com.mdmoney.domain.Currency
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportPlannerTest {

    private fun tx(day: Int, desc: String, amount: Double, kind: TxKind, dir: Direction = Direction.DEBIT, verified: Boolean = true, cat: String? = null) =
        StatementTransaction(SimpleDate(2026, 8, day), desc, amount, dir, kind, cat, if (verified) null else Doubt.READERS_DISAGREE)

    private val lines = listOf(
        tx(1, "Mercado", 50.0, TxKind.PURCHASE, cat = "food"),
        tx(2, "Tarifa", 12.9, TxKind.FEE),
        tx(3, "Salário", 1000.0, TxKind.INCOME, Direction.CREDIT),
        tx(4, "Pagamento de fatura", 500.0, TxKind.CARD_PAYMENT),
        tx(5, "Inventado", 9.99, TxKind.PURCHASE, verified = false),
        tx(6, "Pedágio", 10.0, TxKind.PURCHASE),
        tx(6, "Pedágio", 10.0, TxKind.PURCHASE),
    )

    @Test
    fun only_verified_new_spending_is_ticked_at_first() {
        val existing = mapOf(EntryKey.of(LedgerEntry("20260806", "pedágio ", 10.0)) to 1)
        val rows = ImportPlanner.initialRows(lines, existing)
        assertEquals(listOf(true, true, false, false, false, false, true), rows.map { it.selected })
        // Two honest identical tolls, one already in the vault: only the second is new.
        assertEquals(listOf(false, false, false, false, false, true, false), rows.map { it.alreadyImported })
    }

    @Test
    fun ticked_rows_become_ledger_entries() {
        val rows = ImportPlanner.initialRows(lines, emptyMap())
            .map { if (it.tx.kind == TxKind.INCOME) it.copy(selected = true) else it }
            .map { if (it.id == 0) it.copy(description = "Mercado | Centro") else it }
        val entries = ImportPlanner.entries(rows, listOf(Category("food", "Alimentação")), "Importado", "Entradas")

        assertEquals(5, entries.size)
        val food = entries.first()
        assertEquals("Alimentação", food.title, "a categorised line goes to the category's ledger")
        assertEquals("food", food.category)
        assertEquals(Month.AUG, food.month)
        assertEquals(LedgerEntry("20260801", "Mercado / Centro", 50.0), food.entry, "a pipe would break the table")
        assertEquals("Importado", entries[1].title, "uncategorised spending")
        val income = entries.single { it.income }
        assertEquals("Entradas", income.title)
        assertEquals(1000.0, income.entry.amount)
    }

    @Test
    fun a_chosen_group_wins_over_the_category_and_income_takes_its_own() {
        val rows = ImportPlanner.initialRows(lines, emptyMap())
            .map {
                if (it.tx.kind == TxKind.INCOME) it.copy(selected = true, group = GroupChoice("Salários", "2026 - Salários"))
                else it.copy(group = GroupChoice("Mercado & Casa"))
            }
        val entries = ImportPlanner.entries(rows, listOf(Category("food", "Alimentação")), "Importado", "Entradas")

        // The categorised line keeps its category but lands in the chosen note.
        assertEquals("Mercado & Casa", entries.first().title)
        assertEquals("food", entries.first().category)
        assertEquals("Mercado & Casa", entries[1].title, "uncategorised spending too")
        assertEquals("Salários", entries.single { it.income }.title)
        assertEquals("2026 - Salários", entries.single { it.income }.noteId, "a plain note is addressed by id")
        assertEquals(null, entries.first().noteId, "a ledger group has none")

        val ungrouped = ImportPlanner.entries(
            rows.map { if (it.isIncome) it.copy(group = null) else it }, emptyList(), "Importado", "Entradas",
        )
        assertEquals("Entradas", ungrouped.single { it.income }.title, "no pick keeps the default income note")
    }

    @Test
    fun groups_offered_are_recurring_and_ledger_notes_of_the_right_direction_and_year() {
        fun note(id: String, title: String, year: Int, ledger: Boolean, type: ExpenseType) =
            Expense.empty("a", year, type).copy(id = id, title = title, ledger = ledger)
        val groups = AccountGroups(
            listOf(
                note("w26", "Walmart", 2026, false, ExpenseType.RECURRING_VARIABLE),
                note("w25", "Walmart", 2025, false, ExpenseType.RECURRING_VARIABLE),
                note("2026 Aug - Mercado", "Mercado", 2026, true, ExpenseType.EVENTUAL),
                note("2025 Aug - Mercado", "Mercado", 2025, true, ExpenseType.EVENTUAL),
                note("s26", "Salario", 2026, false, ExpenseType.INCOME),
            ),
        )
        val spend = ImportPlanner.initialRows(listOf(tx(1, "x", 1.0, TxKind.PURCHASE)), emptyMap()).single()
        val income = ImportPlanner.initialRows(listOf(tx(1, "y", 1.0, TxKind.INCOME, Direction.CREDIT)), emptyMap()).single()

        assertEquals(
            listOf(GroupChoice("Mercado"), GroupChoice("Walmart", "w26")),
            groups.forRow(spend),
            "this year's Walmart note, and the ledger group once however many months it has",
        )
        assertEquals(listOf(GroupChoice("Salario", "s26")), groups.forRow(income))
    }

    @Test
    fun switching_account_reflags_without_losing_edits() {
        val rows = ImportPlanner.initialRows(lines, emptyMap()).map { if (it.id == 0) it.copy(description = "Feira") else it }
        val other = mapOf(EntryKey.of(LedgerEntry("20260801", "Feira", 50.0)) to 1)
        val reflagged = ImportPlanner.markExisting(rows, other)
        assertTrue(reflagged[0].alreadyImported)
        assertFalse(reflagged[0].selected)
        assertEquals("Feira", reflagged[0].description)
        assertTrue(reflagged[1].selected)
    }

    @Test
    fun a_currency_mismatch_warns_only_when_both_are_known() {
        assertTrue(ImportPlanner.currencyMismatch("USD", Currency.BRL))
        assertFalse(ImportPlanner.currencyMismatch("BRL", Currency.BRL))
        assertFalse(ImportPlanner.currencyMismatch("USD", Currency.NONE))
        assertFalse(ImportPlanner.currencyMismatch("USD", Currency.custom("US$")))
        assertFalse(ImportPlanner.currencyMismatch(null, Currency.BRL))
    }

    private fun reconciliation(declaredDebits: Double?, declaredCredits: Double? = null) =
        Reconciliation(debits = 100.0, credits = 20.0, declaredDebits = declaredDebits, declaredCredits = declaredCredits)

    @Test
    fun only_a_printed_total_that_disagrees_asks_for_a_review() {
        assertTrue(ImportPlanner.needsAmountReview(reconciliation(100.1)))
        assertTrue(ImportPlanner.needsAmountReview(reconciliation(100.0, declaredCredits = 25.0)))
        assertFalse(ImportPlanner.needsAmountReview(reconciliation(100.0, declaredCredits = 20.0)))
        assertFalse(ImportPlanner.needsAmountReview(reconciliation(null)), "a statement that prints no total can't disagree")
    }

    @Test
    fun correcting_an_amount_retotals_the_statement() {
        val header = StatementHeader(declaredDebits = 594.8)
        val rows = ImportPlanner.initialRows(lines, emptyMap())
        // Read: 50 + 12.9 + 500 + 10 + 10 = 582.90 (the unverified 9.99 doesn't count); printed: 594.80.
        assertEquals(582.9, ImportPlanner.reconcile(rows, header).debits)
        assertEquals(false, ImportPlanner.reconcile(rows, header).debitsMatch)

        val fixed = ImportPlanner.withAmount(rows, id = 0, amount = 61.9, existing = emptyMap())
        assertEquals(61.9, fixed[0].tx.amount)
        assertEquals(594.8, ImportPlanner.reconcile(fixed, header).debits)
        assertEquals(true, ImportPlanner.reconcile(fixed, header).debitsMatch)
        assertEquals(rows.drop(1), fixed.drop(1), "the other rows are left as they were")
    }

    @Test
    fun a_corrected_amount_is_kept_to_cents_and_must_be_positive() {
        val rows = ImportPlanner.initialRows(lines, emptyMap())
        assertEquals(61.9, ImportPlanner.withAmount(rows, 0, 61.899999, emptyMap())[0].tx.amount)
        assertEquals(rows, ImportPlanner.withAmount(rows, 0, 0.0, emptyMap()))
        assertEquals(rows, ImportPlanner.withAmount(rows, 0, -5.0, emptyMap()))
    }

    @Test
    fun a_corrected_amount_can_turn_out_to_be_already_imported() {
        val rows = ImportPlanner.initialRows(lines, mapOf(EntryKey.of(LedgerEntry("20260801", "Mercado", 55.0)) to 1))
        assertFalse(rows[0].alreadyImported, "50.00 isn't in the vault")
        val fixed = ImportPlanner.withAmount(rows, 0, 55.0, mapOf(EntryKey.of(LedgerEntry("20260801", "Mercado", 55.0)) to 1))
        assertTrue(fixed[0].alreadyImported, "but 55.00 is")
        assertFalse(fixed[0].selected)
    }

    @Test
    fun the_review_walks_every_row_then_stops() {
        assertEquals(1, ImportPlanner.nextReview(0, 3))
        assertEquals(2, ImportPlanner.nextReview(1, 3))
        assertEquals(null, ImportPlanner.nextReview(2, 3))
    }
}
