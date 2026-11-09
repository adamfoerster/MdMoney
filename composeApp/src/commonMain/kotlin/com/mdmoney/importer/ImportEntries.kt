package com.mdmoney.importer

import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import kotlin.math.round

/**
 * One reviewed statement line on its way into the vault: a row for the ledger note
 * `<year> <Mon> - <title>.md`, which is spending or, when [income], money received.
 */
data class ImportEntry(
    val year: Int,
    val month: Month,
    val title: String,
    val category: String?,
    val income: Boolean,
    val entry: LedgerEntry,
    /**
     * Set when the user filed the line under an existing plain (recurring or income) note: the id of
     * that note, whose month amount grows by the line instead of a ledger getting a row.
     */
    val noteId: String? = null,
    /** Set when the user filed the line under no group: it becomes a note of its own. */
    val standalone: Boolean = false,
    /** Set when the user is creating a recurring item for the line: the note to make, of this type. */
    val newType: ExpenseType? = null,
    /** For a new fixed bill: its amount per month and the months it repeats in. */
    val newAmount: Double? = null,
    val newMonths: Set<Month> = emptySet(),
    /**
     * Set when the line is one side of a transfer: the account on the other side, and the title of
     * the group the mirrored row takes there. The line itself goes to a group of [title] marked as
     * a transfer with that account.
     */
    val counterAccount: String? = null,
    val counterTitle: String? = null,
)

/** What an import did: rows written, rows already in the vault (skipped), notes touched. */
data class ImportResult(val added: Int, val skipped: Int, val notes: Int)

/**
 * A ledger row's identity for spotting a line that is already in the vault: same day, same note
 * (ignoring case and spacing), same amount to the cent. The category doesn't count — re-importing a
 * statement after filing a line elsewhere must still recognise it.
 */
data class EntryKey(val date: String, val note: String, val cents: Long) {
    companion object {
        fun of(entry: LedgerEntry) = EntryKey(
            date = entry.date,
            note = entry.note.trim().lowercase().replace(Regex("""\s+"""), " "),
            cents = round(entry.amount * 100).toLong(),
        )
    }
}

/**
 * A row that sits in a group marked as one side of a transfer, as the vault holds it. A statement
 * line on the other side of the same transfer describes it in its own words, so it is recognised by
 * day, amount and direction rather than by note ([ImportDedup.alreadyPresent]).
 */
data class TransferEntry(val key: EntryKey, val income: Boolean) {
    /** Same day, same amount to the cent and same direction, whatever the note says. */
    fun sameMoney(other: EntryKey, otherIncome: Boolean) =
        income == otherIncome && key.date == other.date && key.cents == other.cents
}

object ImportDedup {
    /**
     * As [alreadyPresent], also recognising the other side of a transfer: a candidate (with its
     * direction in [incomes]) that matches a [transfers] row on day, amount and direction is already
     * in the vault even if the notes differ — the transfer was written from the other account's
     * import. Each vault row is used once.
     */
    fun alreadyPresent(
        candidates: List<EntryKey>,
        incomes: List<Boolean>,
        existing: Map<EntryKey, Int>,
        transfers: List<TransferEntry>,
    ): List<Boolean> {
        if (transfers.isEmpty()) return alreadyPresent(candidates, existing)
        val remaining = existing.toMutableMap()
        val unused = transfers.toMutableList()
        fun take(key: EntryKey) {
            remaining[key] = ((remaining[key] ?: 0) - 1).coerceAtLeast(0)
        }
        return candidates.mapIndexed { i, key ->
            val income = incomes[i]
            // A row of a transfer group with this very note: the same line again.
            val exact = unused.indexOfFirst { it.income == income && it.key == key }
            if (exact >= 0) {
                unused.removeAt(exact)
                take(key)
                return@mapIndexed true
            }
            val left = remaining[key] ?: 0
            if (left > 0) {
                remaining[key] = left - 1
                return@mapIndexed true
            }
            val other = unused.indexOfFirst { it.sameMoney(key, income) }
            if (other >= 0) {
                take(unused[other].key)
                unused.removeAt(other)
                true
            } else {
                false
            }
        }
    }

    /**
     * Which of [candidates] the vault already holds, given how many times each key is there
     * ([existing]). Counted, not merely looked up: a statement can honestly list the same $10 toll
     * twice on one day, and if the vault has one of them only the second is new.
     */
    fun alreadyPresent(candidates: List<EntryKey>, existing: Map<EntryKey, Int>): List<Boolean> {
        val remaining = existing.toMutableMap()
        return candidates.map { key ->
            val left = remaining[key] ?: 0
            if (left > 0) {
                remaining[key] = left - 1
                true
            } else {
                false
            }
        }
    }

    fun count(entries: Iterable<LedgerEntry>): Map<EntryKey, Int> =
        entries.groupingBy { EntryKey.of(it) }.eachCount()
}
