package com.mdmoney.importer

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

object ImportDedup {
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
