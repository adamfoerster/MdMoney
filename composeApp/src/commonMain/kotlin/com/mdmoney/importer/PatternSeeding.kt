package com.mdmoney.importer

import com.mdmoney.data.LedgerMapper
import com.mdmoney.data.MatchPatterns
import com.mdmoney.domain.MatchPattern

/**
 * Gives an account's existing notes the `match:` lists an import would have taught them, from the
 * statement lines their tables already hold — so the first import after this feature already files
 * lines the way the vault does.
 */
object PatternSeeding {

    /** One note of an account: its id, its content, and the item it belongs to (see [itemOf]). */
    data class Note(val id: String, val content: String)

    /**
     * The patterns each note should gain, by note id — only notes that gain something. Each note
     * learns from its own rows, by the same rule as an import ([MatchPattern.learnFrom]): skipped
     * when a pattern of the note already takes the row, and never one that would take a row of
     * another item in the account (falling back to the whole description, then to nothing).
     */
    fun plan(notes: List<Note>): Map<String, List<String>> {
        val rows = notes.flatMap { n -> LedgerMapper.tableEntries(n.content).map { Triple(n.id, itemOf(n), it.note) } }
        val out = linkedMapOf<String, List<String>>()
        for (n in notes) {
            val item = itemOf(n)
            val known = MatchPatterns.read(n.content).toMutableList()
            val added = mutableListOf<String>()
            val elsewhere = rows.filter { it.second != item }.map { it.third }
            for (entry in LedgerMapper.tableEntries(n.content)) {
                if (known.any { it.matches(entry.note) }) continue
                val text = listOfNotNull(MatchPattern.learnFrom(entry.note), entry.note.trim().takeIf { it.isNotEmpty() }?.let { "$it*" })
                    .firstOrNull { t -> val p = MatchPattern.of(t); p != null && elsewhere.none { p.matches(it) } }
                    ?: continue
                known += MatchPattern.of(text)!!
                added += text
            }
            if (added.isNotEmpty()) out[n.id] = added
        }
        return out
    }

    /** A ledger is its group (direction and title, whatever the month); a plain note is itself. */
    private fun itemOf(n: Note): String {
        val ledger = LedgerMapper.read(n.id, "", n.content, 0) ?: return "note:${n.id}"
        return "group:${ledger.income}:${ledger.title.lowercase()}"
    }
}
