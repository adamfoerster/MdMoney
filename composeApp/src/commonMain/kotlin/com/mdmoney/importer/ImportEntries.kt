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
    /**
     * A pattern for the note the line lands in to add to its `match:` list, so a line like it is
     * filed there on its own next time; null when there is nothing to learn (see
     * [ImportPlanner.learnedPattern]).
     */
    val learn: String? = null,
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

        /** Shortest note that may stand for a longer one that merely continues it. */
        private const val MIN_PREFIX = 10

        /** Separator-only words: how OFX joins the fields a PDF writes with plain spaces. */
        private val SEPARATORS = setOf("-", "–", "—")

        private fun loose(note: String): String =
            note.split(' ').filter { it.isNotEmpty() && it !in SEPARATORS }.joinToString(" ")
    }

    /**
     * Whether [other] is this same statement line worded differently: same day and amount, and notes
     * that are equal or one is the beginning of the other once the " - " separators are ignored.
     * A PDF cuts the description after its first line and writes `Pix NAME`; an OFX keeps all of it
     * and writes `Pix - NAME - CNPJ - BANK (0033) Agência…`. Day and amount to the cent must still
     * agree, so the loose note comparison never has to carry the match alone.
     */
    fun sameLine(other: EntryKey): Boolean {
        if (date != other.date || cents != other.cents) return false
        if (note == other.note) return true
        val a = loose(note)
        val b = loose(other.note)
        val (short, long) = if (a.length <= b.length) a to b else b to a
        return short.length >= MIN_PREFIX && long.startsWith(short)
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
        val remaining = existing.expand()
        val unused = transfers.toMutableList()
        return candidates.mapIndexed { i, key ->
            val income = incomes[i]
            // A row of a transfer group with this very note: the same line again.
            val exact = unused.indexOfFirst { it.income == income && it.key.sameLine(key) }
            if (exact >= 0) {
                remaining.takeLike(unused.removeAt(exact).key)
                return@mapIndexed true
            }
            if (remaining.takeLike(key)) return@mapIndexed true
            val other = unused.indexOfFirst { it.sameMoney(key, income) }
            if (other >= 0) {
                remaining.takeLike(unused.removeAt(other).key)
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
        val remaining = existing.expand()
        return candidates.map { key -> remaining.takeLike(key) }
    }

    private fun Map<EntryKey, Int>.expand(): MutableList<EntryKey> =
        entries.flatMap { (key, n) -> List(n) { key } }.toMutableList()

    /** Uses up one row that is this line — the identical one first, else the same line worded differently. */
    private fun MutableList<EntryKey>.takeLike(key: EntryKey): Boolean {
        val i = indexOf(key).takeIf { it >= 0 } ?: indexOfFirst { it.sameLine(key) }
        if (i < 0) return false
        removeAt(i)
        return true
    }

    fun count(entries: Iterable<LedgerEntry>): Map<EntryKey, Int> =
        entries.groupingBy { EntryKey.of(it) }.eachCount()
}
