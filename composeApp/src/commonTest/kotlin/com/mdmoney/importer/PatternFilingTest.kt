package com.mdmoney.importer

import com.mdmoney.domain.Category
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.MatchPattern
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Statement import filing lines by the notes' `match:` lists, and learning new ones. */
class PatternFilingTest {

    private fun tx(desc: String, amount: Double = 10.0, dir: Direction = Direction.DEBIT, year: Int = 2026, month: Int = 9, cat: String? = null) =
        StatementTransaction(SimpleDate(year, month, 8), desc, amount, dir, if (dir == Direction.CREDIT) TxKind.INCOME else TxKind.PURCHASE, cat, null)

    private fun ledger(title: String, category: String?, year: Int, month: Month, vararg patterns: String, income: Boolean = false) =
        Expense.empty("Regions", year, if (income) ExpenseType.INCOME else ExpenseType.EVENTUAL).copy(
            id = "$year ${month.englishShort} - $title",
            title = title,
            category = category,
            ledger = true,
            amounts = Month.ALL.associateWith { if (it == month) 1.0 else null },
            paid = Month.ALL.associateWith { it == month },
            patterns = patterns.mapNotNull { MatchPattern.of(it) },
        )

    private fun plain(id: String, title: String, type: ExpenseType, category: String?, year: Int, vararg patterns: String) =
        Expense.empty("Regions", year, type).copy(id = id, title = title, category = category, patterns = patterns.mapNotNull { MatchPattern.of(it) })

    private val groups = AccountGroups(
        listOf(
            ledger("Groceries", "groceries", 2026, Month.AUG, "Card Purchase Publix*", "PIN Purchase Wal-Mart*"),
            ledger("Groceries", "groceries", 2026, Month.SEP, "*Aldi*"),
            ledger("Food", "food", 2026, Month.SEP, "Card Purchase Publix Deli*"),
            ledger("Shopping", "Shopping", 2026, Month.JUL, "Card Purchase Amazon*"),
            ledger("Casa", "home", 2026, Month.JUN, "Card Purchase Amazon*"),
            plain("Internet - 2026", "Internet", ExpenseType.RECURRING_VARIABLE, "House", 2026, "Recurring Card Transaction Blue Stream*"),
            ledger("Received", "Salário", 2026, Month.JUL, "'deel,*", income = true),
        ),
    )

    private fun filed(desc: String, dir: Direction = Direction.DEBIT, year: Int = 2026) =
        ImportPlanner.initialRows(listOf(tx(desc, dir = dir, year = year)), emptyMap(), groups = groups).single()

    @Test
    fun a_matching_line_is_filed_under_its_group_with_the_groups_kind_and_category() {
        val row = filed("Card Purchase Publix #1899 5411 Palm Bay FL 32907 3914")
        assertEquals(GroupChoice("Groceries"), row.group)
        assertEquals(ExpenseType.EVENTUAL, row.entryType)
        assertEquals("groceries", row.category)
        assertEquals("Card Purchase Publix*", row.matched?.text)
    }

    @Test
    fun a_ledger_groups_patterns_pool_across_its_months() {
        // `*Aldi*` sits on the September note, `Wal-Mart` on August's: both file into Groceries.
        assertEquals(GroupChoice("Groceries"), filed("Card Purchase Aldi 72 Melbourne").group)
        assertEquals(GroupChoice("Groceries"), filed("PIN Purchase Wal-Mart #5455 5411 Palm Bay 3914").group)
    }

    @Test
    fun a_plain_bill_takes_its_line_as_that_note_of_its_kind() {
        val row = filed("Recurring Card Transaction Blue Stream 4899 954-753-0100 FL 33065 3914")
        assertEquals(GroupChoice("Internet", "Internet - 2026"), row.group)
        assertEquals(ExpenseType.RECURRING_VARIABLE, row.entryType)
        assertEquals("House", row.category)
        // ...but only for its own year: a 2027 line has no 2026 bill to grow.
        assertNull(filed("Recurring Card Transaction Blue Stream 4899", year = 2027).group)
    }

    @Test
    fun the_most_specific_pattern_wins_and_a_tie_goes_to_the_most_recent_group() {
        assertEquals(GroupChoice("Food"), filed("Card Purchase Publix Deli 5814 Palm Bay").group, "25 literal characters beat 20")
        // Shopping (July) and Casa (June) say the same thing: July is how lines are filed now.
        assertEquals(GroupChoice("Shopping"), filed("Card Purchase Amazon Mktpl*8y 5942").group)
    }

    @Test
    fun money_in_only_matches_income_and_money_out_only_spending() {
        assertEquals(GroupChoice("Received"), filed("'deel, Iryakeivz Payments Adam Foerster", Direction.CREDIT).group)
        assertNull(filed("'deel, Iryakeivz Payments Adam Foerster", Direction.DEBIT).group)
        assertNull(filed("Card Purchase Publix #1899", Direction.CREDIT).group, "a refund from Publix isn't groceries spending")
    }

    @Test
    fun a_line_no_pattern_claims_keeps_the_category_it_was_read_with() {
        val row = ImportPlanner.initialRows(listOf(tx("Card Purchase Target 5411", cat = "Shopping")), emptyMap(), groups = groups).single()
        assertNull(row.group)
        assertNull(row.entryType)
        assertNull(row.matched)
        assertEquals("Shopping", row.category)
    }

    @Test
    fun another_account_refiles_what_patterns_decided_but_not_what_the_user_chose() {
        val rows = ImportPlanner.initialRows(
            listOf(tx("Card Purchase Publix #1899", cat = "x"), tx("Card Purchase Target", cat = "y")),
            emptyMap(),
            groups = groups,
        ).map { if (it.id == 1) it.copy(entryType = ExpenseType.RECURRING_FIXED, category = "mine") else it }
        val other = AccountGroups(listOf(ledger("Mercado", "groceries", 2026, Month.SEP, "card purchase publix*")))

        val refiled = ImportPlanner.refile(rows, other)
        assertEquals(GroupChoice("Mercado"), refiled[0].group, "the pattern-filed line follows the new account's patterns")
        assertEquals(ExpenseType.RECURRING_FIXED, refiled[1].entryType, "the user's kind stays")
        assertEquals("mine", refiled[1].category)
        assertNull(refiled[1].group)

        val none = ImportPlanner.refile(rows, AccountGroups())
        assertNull(none[0].group)
        assertNull(none[0].entryType)
        assertEquals("x", none[0].category, "back to the category it was read with")
    }

    // --- learning ---

    private val cats = listOf(Category("groceries", "Groceries"), Category("food", "Food"))

    private fun entries(rows: List<ReviewRow>, g: AccountGroups = groups) =
        ImportPlanner.entries(rows.map { it.copy(selected = true) }, cats, "Imported", "Received", "Regions", groups = g)

    @Test
    fun a_line_filed_by_hand_teaches_its_group_the_merchant() {
        val rows = ImportPlanner.initialRows(listOf(tx("Card Purchase Target T-2547 5411 Melbourne FL 3914", cat = "groceries")), emptyMap())
        val e = entries(rows, AccountGroups()).single()
        assertEquals("Groceries", e.title)
        assertEquals("Card Purchase Target*", e.learn, "T-2547 is the store's code: the cut")
    }

    @Test
    fun a_line_its_group_already_recognises_teaches_that_same_pattern() {
        // So the month's note the line lands in carries the pattern too, rather than a variant of it.
        val rows = ImportPlanner.initialRows(listOf(tx("Card Purchase Aldi 72 Melbourne")), emptyMap(), groups = groups)
        assertEquals("*Aldi*", entries(rows).single().learn)
    }

    @Test
    fun a_learned_pattern_never_takes_a_line_the_user_filed_elsewhere_in_the_same_import() {
        val rows = ImportPlanner.initialRows(
            listOf(
                tx("Card Purchase Amazon Mktpl*8y 5942 Amzn.Com/Bill", cat = "groceries"),
                tx("Card Purchase Amazon Mktpl*n4 5942 Amzn.Com/Bill", cat = "food"),
            ),
            emptyMap(),
        )
        val (a, b) = entries(rows, AccountGroups())
        // "Card Purchase Amazon Mktpl*" would take both lines, which went to different groups.
        assertEquals("Card Purchase Amazon Mktpl*8y 5942 Amzn.Com/Bill*", a.learn)
        assertEquals("Card Purchase Amazon Mktpl*n4 5942 Amzn.Com/Bill*", b.learn)
        // Identical descriptions filed apart can't be told apart at all: nothing is learned.
        val same = ImportPlanner.initialRows(listOf(tx("Card Purchase Amazon", cat = "groceries"), tx("Card Purchase Amazon", cat = "food")), emptyMap())
        assertEquals(listOf(null, null), entries(same, AccountGroups()).map { it.learn })
    }

    @Test
    fun lines_filed_together_dont_block_each_other() {
        val rows = ImportPlanner.initialRows(
            listOf(tx("Card Purchase Publix #1899 5411", cat = "groceries"), tx("Card Purchase Publix #1900 5411", cat = "groceries")),
            emptyMap(),
        )
        assertEquals(listOf("Card Purchase Publix*", "Card Purchase Publix*"), entries(rows, AccountGroups()).map { it.learn })
    }

    @Test
    fun transfers_and_lines_filed_as_notes_of_their_own_teach_nothing() {
        val rows = ImportPlanner.initialRows(listOf(tx("Zelle to Ana 123"), tx("Card Purchase Lamp Store 99")), emptyMap())
            .map { if (it.id == 0) it.copy(transfer = true, transferAccount = "Nubank") else it.copy(group = GroupChoice.NONE) }
        assertEquals(listOf(null, null), entries(rows, AccountGroups()).map { it.learn })
    }
}
