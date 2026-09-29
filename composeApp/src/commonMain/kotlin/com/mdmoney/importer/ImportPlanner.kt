package com.mdmoney.importer

import com.mdmoney.domain.Category
import com.mdmoney.domain.Currency
import com.mdmoney.domain.Expense
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
    /** Already in the vault from an earlier import; never ticked by default. */
    val alreadyImported: Boolean,
) {
    val isIncome: Boolean get() = tx.direction == Direction.CREDIT
}

/**
 * Where a line can be filed: a group by [title]. With a [noteId] it is an existing plain note
 * (a recurring bill or an income line) whose month grows by the line; without one it is a ledger
 * group, whose note for the line's month is found or created.
 */
data class GroupChoice(val title: String, val noteId: String? = null)

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
}

/** The review screen's logic, kept out of the composable so it can be tested. */
object ImportPlanner {

    /**
     * The rows as first shown. Ticked only when the line is spending ([TxKind.preselected]), its
     * amount was found in the statement, and the vault doesn't already hold it.
     */
    fun initialRows(transactions: List<StatementTransaction>, existing: Map<EntryKey, Int>): List<ReviewRow> {
        val present = ImportDedup.alreadyPresent(transactions.map { EntryKey.of(entryOf(it, it.description)) }, existing)
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
    fun markExisting(rows: List<ReviewRow>, existing: Map<EntryKey, Int>): List<ReviewRow> {
        val present = ImportDedup.alreadyPresent(rows.map { EntryKey.of(entryOf(it.tx, it.description)) }, existing)
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
    ): List<ImportEntry> = rows.filter { it.selected }.map { row ->
        val title = when {
            row.group != null -> row.group.title
            row.isIncome -> incomeTitle
            else -> categoryTitle(row.category, categories) ?: uncategorizedTitle
        }
        ImportEntry(
            year = row.tx.date.year,
            month = Month.ALL.first { it.number == row.tx.date.month },
            title = title,
            category = row.category,
            income = row.isIncome,
            entry = entryOf(row.tx, row.description),
            noteId = row.group?.noteId,
        )
    }

    /**
     * True when a total the statement prints disagrees with the lines read from it — the cue to go
     * through them against the PDF. A total the statement doesn't print can't disagree.
     */
    fun needsAmountReview(r: Reconciliation): Boolean = r.debitsMatch == false || r.creditsMatch == false

    /**
     * [rows] with row [id]'s amount corrected to [amount] (in cents; anything not positive is
     * ignored). The amount is part of what makes a line a duplicate, so every row is re-checked
     * against the vault's [existing] entries.
     */
    fun withAmount(rows: List<ReviewRow>, id: Int, amount: Double, existing: Map<EntryKey, Int>): List<ReviewRow> {
        val cents = kotlin.math.round(amount * 100) / 100
        if (cents <= 0.0) return rows
        val changed = rows.map { if (it.id == id) it.copy(tx = it.tx.copy(amount = cents)) else it }
        return markExisting(changed, existing)
    }

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
