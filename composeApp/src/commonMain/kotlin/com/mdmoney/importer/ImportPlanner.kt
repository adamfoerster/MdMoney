package com.mdmoney.importer

import com.mdmoney.domain.Category
import com.mdmoney.domain.Currency
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
    /** Already in the vault from an earlier import; never ticked by default. */
    val alreadyImported: Boolean,
) {
    val isIncome: Boolean get() = tx.direction == Direction.CREDIT
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
     * category) or to [uncategorizedTitle]; money received goes to one [incomeTitle] ledger a month.
     */
    fun entries(
        rows: List<ReviewRow>,
        categories: List<Category>,
        uncategorizedTitle: String,
        incomeTitle: String,
    ): List<ImportEntry> = rows.filter { it.selected }.map { row ->
        val title = if (row.isIncome) incomeTitle else categoryTitle(row.category, categories) ?: uncategorizedTitle
        ImportEntry(
            year = row.tx.date.year,
            month = Month.ALL.first { it.number == row.tx.date.month },
            title = title,
            category = row.category,
            income = row.isIncome,
            entry = entryOf(row.tx, row.description),
        )
    }

    /**
     * True when the statement's currency is known and differs from the account's. An account with no
     * currency, or a custom symbol that can't be compared, never warns.
     */
    fun currencyMismatch(statement: String?, account: Currency): Boolean =
        statement != null && account.hasSymbol && !account.isCustom && !account.code.equals(statement, ignoreCase = true)

    private fun entryOf(t: StatementTransaction, description: String) =
        LedgerEntry(t.date.toLedgerDate(), description.trim().replace('|', '/'), t.amount)
}
