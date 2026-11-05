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
import kotlin.test.assertNull
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
    fun a_category_created_in_the_picker_files_the_line_under_a_ledger_titled_like_it() {
        val rows = ImportPlanner.initialRows(lines, emptyMap()).filter { it.tx.description == "Mercado" }
            .map { it.copy(category = "pet shop") }
        val entries = ImportPlanner.entries(rows, listOf(Category("food", "Alimentação")), "Importado", "Entradas")

        assertEquals("Pet Shop", entries.single().title)
        assertEquals("pet shop", entries.single().category)
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
    fun a_line_filed_under_no_group_becomes_a_standalone_entry_named_after_it() {
        val rows = ImportPlanner.initialRows(lines, emptyMap())
            .map { if (it.id == 0) it.copy(description = "Mercado Bella", group = GroupChoice.NONE) else it }
        val entries = ImportPlanner.entries(rows, listOf(Category("food", "Alimentação")), "Importado", "Entradas")

        val alone = entries.first()
        assertTrue(alone.standalone)
        assertEquals("Mercado Bella", alone.title)
        assertEquals("food", alone.category, "the category still travels with it")
        assertFalse(entries[1].standalone, "the others stay in their groups")
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

    private fun note(id: String, title: String, year: Int, ledger: Boolean, type: ExpenseType) =
        Expense.empty("a", year, type).copy(id = id, title = title, ledger = ledger)

    private val typed = AccountGroups(
        listOf(
            note("w26", "Walmart", 2026, false, ExpenseType.RECURRING_VARIABLE),
            note("w25", "Walmart", 2025, false, ExpenseType.RECURRING_VARIABLE),
            note("net26", "Netflix", 2026, false, ExpenseType.RECURRING_FIXED),
            note("2026 Aug - Mercado", "Mercado", 2026, true, ExpenseType.EVENTUAL),
            note("viagem", "Viagem", 2026, false, ExpenseType.EVENTUAL),
            note("s26", "Salario", 2026, false, ExpenseType.INCOME),
            note("2026 Aug - Bônus", "Bônus", 2026, true, ExpenseType.INCOME),
        ),
    )
    private val spendRow = ImportPlanner.initialRows(listOf(tx(1, "x", 1.0, TxKind.PURCHASE)), emptyMap()).single()
    private val incomeRow = ImportPlanner.initialRows(listOf(tx(1, "y", 1.0, TxKind.INCOME, Direction.CREDIT)), emptyMap()).single()

    @Test
    fun each_kind_lists_only_its_own_items() {
        assertEquals(listOf(GroupChoice("Walmart", "w26")), typed.forType(spendRow, ExpenseType.RECURRING_VARIABLE), "this year's, variable only")
        assertEquals(listOf(GroupChoice("Netflix", "net26")), typed.forType(spendRow, ExpenseType.RECURRING_FIXED))
        assertEquals(listOf(GroupChoice("Mercado"), GroupChoice("Viagem", "viagem")), typed.forType(spendRow, ExpenseType.EVENTUAL))
        assertEquals(listOf(GroupChoice("Bônus"), GroupChoice("Salario", "s26")), typed.forType(incomeRow, ExpenseType.INCOME))
    }

    @Test
    fun money_in_can_only_be_income_and_money_out_never_is() {
        assertEquals(listOf(ExpenseType.INCOME), ImportPlanner.typesFor(incomeRow))
        assertEquals(
            listOf(ExpenseType.EVENTUAL, ExpenseType.RECURRING_FIXED, ExpenseType.RECURRING_VARIABLE),
            ImportPlanner.typesFor(spendRow),
        )
    }

    @Test
    fun changing_the_kind_drops_the_item_but_choosing_the_same_one_keeps_it() {
        val filed = spendRow.copy(entryType = ExpenseType.RECURRING_FIXED, group = GroupChoice("Netflix", "net26"))
        assertEquals(filed, ImportPlanner.withType(filed, ExpenseType.RECURRING_FIXED))
        val moved = ImportPlanner.withType(filed, ExpenseType.EVENTUAL)
        assertEquals(ExpenseType.EVENTUAL, moved.entryType)
        assertNull(moved.group, "a recurring note is not a one-off group")
        assertNull(ImportPlanner.withType(moved, null).entryType)
    }

    @Test
    fun a_created_item_is_a_ledger_group_for_one_offs_and_a_note_to_make_for_recurring() {
        assertEquals(GroupChoice("Viagem"), GroupChoice.created(" Viagem ", ExpenseType.EVENTUAL))
        assertEquals(GroupChoice("Bônus"), GroupChoice.created("Bônus", ExpenseType.INCOME))
        val fixed = GroupChoice.created("Netflix", ExpenseType.RECURRING_FIXED)
        assertEquals(ExpenseType.RECURRING_FIXED, fixed.newType)

        val entry = ImportPlanner.entries(
            listOf(spendRow.copy(selected = true, entryType = ExpenseType.RECURRING_FIXED, group = fixed)),
            emptyList(), "Importado", "Entradas",
        ).single()
        assertEquals("Netflix", entry.title)
        assertEquals(ExpenseType.RECURRING_FIXED, entry.newType)
        assertNull(entry.noteId)

        val withTerms = GroupChoice.created("Netflix", ExpenseType.RECURRING_FIXED, 45.0, setOf(Month.JUL, Month.AUG))
        val carried = ImportPlanner.entries(
            listOf(spendRow.copy(selected = true, entryType = ExpenseType.RECURRING_FIXED, group = withTerms)),
            emptyList(), "Importado", "Entradas",
        ).single()
        assertEquals(45.0, carried.newAmount)
        assertEquals(setOf(Month.JUL, Month.AUG), carried.newMonths)
        assertNull(GroupChoice.created("Walmart", ExpenseType.RECURRING_VARIABLE, 45.0, setOf(Month.JUL)).newAmount, "a variable bill has no fixed amount")
    }

    @Test
    fun the_summary_adds_up_money_in_and_out_and_groups_lines_by_item() {
        fun row(id: Int, day: Int, desc: String, amount: Double, dir: Direction = Direction.DEBIT, kind: TxKind = TxKind.PURCHASE, month: Int = 8) =
            ReviewRow(id, StatementTransaction(SimpleDate(2026, month, day), desc, amount, dir, kind, null, null), true, desc, null, alreadyImported = false)
        val netflix = GroupChoice("Netflix", "net26")
        val rows = listOf(
            row(0, 1, "Netflix jan", 39.9).copy(entryType = ExpenseType.RECURRING_FIXED, group = netflix),
            row(1, 9, "Netflix extra", 10.0, month = 9).copy(entryType = ExpenseType.RECURRING_FIXED, group = netflix),
            row(2, 2, "Padaria", 7.5),
            row(3, 3, "Pix", 100.0, Direction.CREDIT, TxKind.INCOME),
            row(4, 4, "Ignorada", 999.0).copy(selected = false),
        )
        val summary = ImportPlanner.summarize(rows, emptyList(), "Importado", "Entradas")

        assertEquals(100.0, summary.credits)
        assertEquals(57.4, summary.debits, "39.90 + 10 + 7.50, the unticked line left out")
        assertEquals(4, summary.lines)
        assertEquals(listOf("Entradas", "Netflix", "Importado"), summary.destinations.map { it.title })
        val n = summary.destinations.single { it.title == "Netflix" }
        assertEquals(2, n.lines)
        assertEquals(49.9, n.total)
        assertEquals(listOf(Month.AUG, Month.SEP), n.months)
        assertEquals(ExpenseType.RECURRING_FIXED, n.type)
        assertTrue(summary.destinations.single { it.title == "Entradas" }.income)
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
    fun a_corrected_amount_is_kept_to_cents_and_cannot_be_negative() {
        val rows = ImportPlanner.initialRows(lines, emptyMap())
        assertEquals(61.9, ImportPlanner.withAmount(rows, 0, 61.899999, emptyMap())[0].tx.amount)
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

    @Test
    fun zero_says_the_line_never_happened() {
        val header = StatementHeader(declaredDebits = 532.9)
        val rows = ImportPlanner.initialRows(lines, emptyMap())
        assertTrue(rows[0].selected)
        // 582.90 read; the 50.00 market purchase was cancelled, so the statement prints 532.90.
        val zeroed = ImportPlanner.withAmount(rows, id = 0, amount = 0.0, existing = emptyMap())
        assertEquals(0.0, zeroed[0].tx.amount)
        assertFalse(zeroed[0].selected, "unticked")
        assertFalse(zeroed[0].selectable, "and can't be ticked again while it is 0")
        assertEquals(true, ImportPlanner.reconcile(zeroed, header).debitsMatch)
        val entries = ImportPlanner.entries(zeroed.map { it.copy(selected = true) }, emptyList(), "Importado", "Entradas")
        assertFalse(entries.any { it.entry.note == "Mercado" }, "a zero line is never written, even if ticked somehow")
    }

    @Test
    fun a_typed_amount_reads_as_statements_print_them() {
        assertEquals(33.08, ImportPlanner.typedAmount("33,08"))
        assertEquals(33.08, ImportPlanner.typedAmount("33.08"))
        assertEquals(1234.56, ImportPlanner.typedAmount("1.234,56"), "the app's own amount parser used to reject this")
        assertEquals(1234.56, ImportPlanner.typedAmount("1,234.56"))
        assertEquals(0.0, ImportPlanner.typedAmount("0"))
        assertEquals(0.0, ImportPlanner.typedAmount("0,00"))
        assertNull(ImportPlanner.typedAmount(""))
        assertNull(ImportPlanner.typedAmount("-5"))
        assertNull(ImportPlanner.typedAmount("abc"))
    }
}
