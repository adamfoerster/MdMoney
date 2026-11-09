package com.mdmoney.importer

import com.mdmoney.domain.Category
import com.mdmoney.domain.Currency
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.domain.categoryTitle

/** One line on the review screen, as the user has left it. */
data class ReviewRow(
    val id: Int,
    val tx: StatementTransaction,
    val selected: Boolean,
    val description: String,
    val category: String?,
    /** An existing group to file the line under; null follows the category. */
    val group: GroupChoice? = null,
    /**
     * The kind of item the line is filed under (income, one-off, recurring fixed or variable), chosen
     * on the review screen; null follows the category. [group] is then an item of that kind.
     */
    val entryType: ExpenseType? = null,
    /** Already in the vault from an earlier import; never ticked by default. */
    val alreadyImported: Boolean,
    /** The line is one side of a transfer; [transferAccount] is the account on the other side, once chosen. */
    val transfer: Boolean = false,
    val transferAccount: String? = null,
) {
    val isIncome: Boolean get() = tx.direction == Direction.CREDIT

    /** Whether ticking it means anything: not already in the vault, and not set to 0 (didn't happen). */
    val selectable: Boolean get() = !alreadyImported && tx.amount > 0
}

/**
 * Where a line can be filed: a group by [title]. With a [noteId] it is an existing plain note
 * (a recurring bill or an income line) whose month grows by the line; without one it is a ledger
 * group, whose note for the line's month is found or created.
 */
data class GroupChoice(
    val title: String,
    val noteId: String? = null,
    val standalone: Boolean = false,
    /** Set on a recurring item the user is creating: the import makes the note, of this type. */
    val newType: ExpenseType? = null,
    /** A new fixed bill's amount per month, and the months it repeats in (the line's own month takes the line's amount). */
    val newAmount: Double? = null,
    val newMonths: Set<Month> = emptySet(),
) {
    companion object {
        /** Under no group at all: the line becomes a note of its own. */
        val NONE = GroupChoice(title = "", standalone = true)

        /** A brand-new item of [type] named [title]: a ledger group for one-offs and income, a note for recurring. */
        fun created(title: String, type: ExpenseType, amount: Double? = null, months: Set<Month> = emptySet()): GroupChoice =
            if (type == ExpenseType.RECURRING_FIXED) {
                GroupChoice(title.trim(), newType = type, newAmount = amount, newMonths = months)
            } else if (type == ExpenseType.RECURRING_VARIABLE) {
                GroupChoice(title.trim(), newType = type)
            } else {
                GroupChoice(title.trim())
            }
    }
}

/** Where some of an import's lines ended up: one item, its kind, the months and lines it took, their sum. */
data class ImportDestination(
    val title: String,
    val type: ExpenseType,
    val months: List<Month>,
    val lines: Int,
    val total: Double,
) {
    val income: Boolean get() = type == ExpenseType.INCOME
}

/** The closing screen's numbers: money received and spent by the imported lines, and where it went. */
data class ImportSummary(val credits: Double, val debits: Double, val destinations: List<ImportDestination>) {
    val lines: Int get() = destinations.sumOf { it.lines }
}

/** Everything an account holds that a statement line could be filed under. */
data class AccountGroups(val notes: List<Expense> = emptyList()) {
    /**
     * The choices for [row]: money in and money out each see their own kind. A plain note only
     * counts for the year it belongs to — the year of the line — and hides a ledger group that
     * shares its title.
     */
    fun forRow(row: ReviewRow): List<GroupChoice> {
        val kind = notes.filter { it.isIncome == row.isIncome }
        val plain = kind.filter { !it.ledger && it.year == row.tx.date.year }.map { GroupChoice(it.title, it.id) }
        val ledgers = kind.filter { it.ledger }.map { GroupChoice(it.title) }
        return (plain + ledgers).distinctBy { it.title }.sortedBy { it.title.lowercase() }
    }

    /**
     * The items of one [type] a line can go under. Income: the income notes and groups. One-off: the
     * ledger groups and one-off notes. Recurring: the notes of exactly that type. A plain note only
     * counts for the year of the line.
     */
    fun forType(row: ReviewRow, type: ExpenseType): List<GroupChoice> {
        val year = row.tx.date.year
        val items = when (type) {
            ExpenseType.INCOME -> notes.filter { it.isIncome }
            ExpenseType.EVENTUAL -> notes.filter { !it.isIncome && (it.ledger || it.type == ExpenseType.EVENTUAL) }
            ExpenseType.RECURRING_FIXED, ExpenseType.RECURRING_VARIABLE -> notes.filter { !it.ledger && it.type == type }
        }
        return items
            .filter { it.ledger || it.year == year }
            .map { if (it.ledger) GroupChoice(it.title) else GroupChoice(it.title, it.id) }
            .distinctBy { it.title }
            .sortedBy { it.title.lowercase() }
    }
}

/** The review screen's logic, kept out of the composable so it can be tested. */
object ImportPlanner {

    /**
     * The rows as first shown. Ticked only when the line is spending ([TxKind.preselected]), its
     * amount was found in the statement, and the vault doesn't already hold it.
     */
    fun initialRows(
        transactions: List<StatementTransaction>,
        existing: Map<EntryKey, Int>,
        transfers: List<TransferEntry> = emptyList(),
    ): List<ReviewRow> {
        val present = ImportDedup.alreadyPresent(
            transactions.map { EntryKey.of(entryOf(it, it.description)) },
            transactions.map { it.direction == Direction.CREDIT },
            existing,
            transfers,
        )
        return transactions.mapIndexed { i, t ->
            ReviewRow(
                id = i,
                tx = t,
                selected = t.kind.preselected && t.verified && !present[i],
                description = t.description,
                category = t.category,
                alreadyImported = present[i],
            )
        }
    }

    /**
     * Re-flags [rows] against another account's contents, keeping the user's edits. A row that turns
     * out to be there already is unticked; one that no longer is stays as the user left it.
     */
    fun markExisting(rows: List<ReviewRow>, existing: Map<EntryKey, Int>, transfers: List<TransferEntry> = emptyList()): List<ReviewRow> {
        val present = ImportDedup.alreadyPresent(
            rows.map { EntryKey.of(entryOf(it.tx, it.description)) },
            rows.map { it.isIncome },
            existing,
            transfers,
        )
        return rows.mapIndexed { i, row ->
            row.copy(alreadyImported = present[i], selected = row.selected && !present[i])
        }
    }

    /**
     * What the ticked rows become. Spending goes to its category's ledger (titled like the
     * category) or to [uncategorizedTitle], unless the row names a group, which wins; money
     * received goes to one [incomeTitle] ledger a month, or to the income group the row names.
     */
    fun entries(
        rows: List<ReviewRow>,
        categories: List<Category>,
        uncategorizedTitle: String,
        incomeTitle: String,
        thisAccount: String = "",
        transferTitle: (account: String, income: Boolean) -> String = { account, _ -> "Transfer $account" },
    ): List<ImportEntry> = rows.filter { it.selected && it.selectable && !needsTransferAccount(it) }.map { row ->
        val other = row.transferAccount?.takeIf { row.transfer }
        val title = if (other != null) transferTitle(other, row.isIncome) else titleOf(row, categories, uncategorizedTitle, incomeTitle)
        ImportEntry(
            year = row.tx.date.year,
            month = Month.ALL.first { it.number == row.tx.date.month },
            title = title,
            category = row.category,
            income = row.isIncome,
            entry = entryOf(row.tx, row.description),
            noteId = row.group?.noteId.takeIf { other == null },
            standalone = row.group?.standalone == true && other == null,
            newType = row.group?.newType.takeIf { other == null },
            newAmount = row.group?.newAmount,
            newMonths = row.group?.newMonths.orEmpty(),
            counterAccount = other,
            // On the other side the direction flips: what leaves here arrives there.
            counterTitle = other?.let { transferTitle(thisAccount, !row.isIncome) },
        )
    }

    private fun titleOf(row: ReviewRow, categories: List<Category>, uncategorizedTitle: String, incomeTitle: String): String = when {
        row.group?.standalone == true -> row.description.trim()
        row.group != null -> row.group.title
        row.isIncome -> incomeTitle
        else -> categoryTitle(row.category, categories) ?: uncategorizedTitle
    }

    /**
     * What an import did, for the closing screen: the money in and out of the ticked lines, and where
     * they went — one destination per item (title and kind), with the months and lines it took.
     * Biggest first; a line filed by category without a chosen kind is a one-off, or income.
     */
    fun summarize(
        rows: List<ReviewRow>,
        categories: List<Category>,
        uncategorizedTitle: String,
        incomeTitle: String,
        transferTitle: (account: String, income: Boolean) -> String = { account, _ -> "Transfer $account" },
    ): ImportSummary {
        val ticked = rows.filter { it.selected && it.selectable && !needsTransferAccount(it) }
        fun title(row: ReviewRow): String {
            val other = row.transferAccount?.takeIf { row.transfer }
            return if (other != null) transferTitle(other, row.isIncome) else titleOf(row, categories, uncategorizedTitle, incomeTitle)
        }
        val destinations = ticked.groupBy { row ->
            val type = row.entryType ?: if (row.isIncome) ExpenseType.INCOME else ExpenseType.EVENTUAL
            type to title(row).lowercase()
        }.map { (key, group) ->
            ImportDestination(
                title = title(group.first()),
                type = key.first,
                months = group.map { r -> Month.ALL.first { it.number == r.tx.date.month } }.distinct().sortedBy { it.number },
                lines = group.size,
                total = kotlin.math.round(group.sumOf { it.tx.amount } * 100) / 100,
            )
        }.sortedByDescending { it.total }
        return ImportSummary(
            credits = kotlin.math.round(ticked.filter { it.isIncome }.sumOf { it.tx.amount } * 100) / 100,
            debits = kotlin.math.round(ticked.filter { !it.isIncome }.sumOf { it.tx.amount } * 100) / 100,
            destinations = destinations,
        )
    }

    /** The kinds of item a line may be filed under: money in is income, money out is anything else. */
    fun typesFor(row: ReviewRow): List<ExpenseType> =
        if (row.isIncome) listOf(ExpenseType.INCOME)
        else listOf(ExpenseType.EVENTUAL, ExpenseType.RECURRING_FIXED, ExpenseType.RECURRING_VARIABLE)

    /** [row] filed under [type]; changing the kind drops the item, which belonged to the old one. */
    fun withType(row: ReviewRow, type: ExpenseType?): ReviewRow =
        if (type == row.entryType && !row.transfer) row else row.copy(entryType = type, group = null, transfer = false, transferAccount = null)

    /** [row] filed as a transfer: no kind, item or category any more, just the account on the other side. */
    fun asTransfer(row: ReviewRow): ReviewRow =
        if (row.transfer) row else row.copy(entryType = null, group = null, transfer = true, transferAccount = null)

    /** Whether a ticked [row] still lacks the account of its transfer. */
    fun needsTransferAccount(row: ReviewRow): Boolean = row.transfer && row.transferAccount == null

    /**
     * True when a total the statement prints disagrees with the lines read from it — the cue to go
     * through them against the PDF. A total the statement doesn't print can't disagree.
     */
    fun needsAmountReview(r: Reconciliation): Boolean = r.debitsMatch == false || r.creditsMatch == false

    /**
     * [rows] with row [id]'s amount corrected to [amount] (in cents; a negative one is ignored).
     * 0 says the line never happened — cancelled, say — so it is unticked and drops out of the
     * totals. The amount is part of what makes a line a duplicate, so every row is re-checked
     * against the vault's [existing] entries.
     */
    fun withAmount(
        rows: List<ReviewRow>,
        id: Int,
        amount: Double,
        existing: Map<EntryKey, Int>,
        transfers: List<TransferEntry> = emptyList(),
    ): List<ReviewRow> {
        val cents = kotlin.math.round(amount * 100) / 100
        if (cents < 0.0) return rows
        val changed = rows.map {
            if (it.id == id) it.copy(tx = it.tx.copy(amount = cents), selected = it.selected && cents > 0) else it
        }
        return markExisting(changed, existing, transfers)
    }

    /**
     * An amount typed on the review screen, read as a statement prints one (`1.234,56`,
     * `1,234.56`, `33,08`, `0`), or null when it can't be read or is negative. 0 is an answer: the
     * line didn't happen (see [withAmount]).
     */
    fun typedAmount(text: String): Double? =
        StatementText.parseAmount(text)?.takeIf { it >= 0.0 && text.none { c -> c.isLetter() } }

    /** The statement's totals against the rows as the user has corrected them. */
    fun reconcile(rows: List<ReviewRow>, header: StatementHeader): Reconciliation =
        StatementExtractor.reconcile(rows.map { it.tx }, header)

    /** The row to review after [position], or null when that was the last. */
    fun nextReview(position: Int, count: Int): Int? = (position + 1).takeIf { it < count }

    /**
     * True when the statement's currency is known and differs from the account's. An account with no
     * currency, or a custom symbol that can't be compared, never warns.
     */
    fun currencyMismatch(statement: String?, account: Currency): Boolean =
        statement != null && account.hasSymbol && !account.isCustom && !account.code.equals(statement, ignoreCase = true)

    private fun entryOf(t: StatementTransaction, description: String) =
        LedgerEntry(t.date.toLedgerDate(), description.trim().replace('|', '/'), t.amount)
}
