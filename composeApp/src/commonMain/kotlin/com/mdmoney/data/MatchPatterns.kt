package com.mdmoney.data

import com.mdmoney.domain.MatchPattern

/**
 * The `match:` list a note keeps of the statement lines that belong in it — what statement import
 * reads to file a line on its own, and adds to whenever a line is filed there:
 *
 * ```yaml
 * match:
 *   - "Card Purchase Publix*"
 *   - "PIN Purchase Wal-Mart*"
 * ```
 *
 * Any note can carry it, a ledger or a plain bill. The key is the user's to edit in Obsidian, so it
 * is read leniently (a block list, an inline `[a, b]` list or a single value; quoted or not) and
 * written by appending: items already there keep their exact text and order, and nothing else in
 * the note changes. See [MatchPattern] for what an item means.
 */
object MatchPatterns {
    const val KEY = "match"

    /** The note's patterns in the order written; empty when it has none. */
    fun read(note: MarkdownNote): List<MatchPattern> = items(note).mapNotNull { MatchPattern.of(it) }

    fun read(content: String): List<MatchPattern> = read(FrontmatterParser.parse(content))

    /**
     * [content] with every pattern of [added] that it doesn't already hold appended to its `match:`
     * list — created after `category:` (else at the end) when the note has none. Returns [content]
     * untouched when there is nothing new, so a note is never rewritten for nothing.
     */
    fun withAdded(content: String, added: List<String>): String {
        val note = FrontmatterParser.parse(content)
        val held = read(note).toMutableSet()
        val fresh = added.mapNotNull { MatchPattern.of(it) }.filter { held.add(it) }
        if (fresh.isEmpty()) return content
        val idx = note.indexOfKey(KEY)
        val newLines = fresh.map { "  - ${quote(it.text)}" }
        // A block list keeps its own lines verbatim; an inline or single value becomes one.
        val lines = (note.entries.getOrNull(idx) as? FmEntry.Raw)?.lines?.let { it + newLines }
            ?: (listOf("$KEY:") + items(note).map { "  - ${quote(it)}" } + newLines)
        val entry = FmEntry.Raw(KEY, lines)
        when {
            idx >= 0 -> note.entries[idx] = entry
            note.indexOfKey("category") >= 0 -> note.entries.add(note.indexOfKey("category") + 1, entry)
            else -> note.entries.add(entry)
        }
        return FrontmatterParser.serialize(note)
    }

    /** The raw item texts, unquoted. */
    private fun items(note: MarkdownNote): List<String> =
        when (val e = note.entries.firstOrNull { it.key == KEY }) {
            is FmEntry.Raw -> e.lines.drop(1).map { it.trim() }.filter { it.startsWith("-") }
                .map { unquote(it.removePrefix("-").trim()) }
            is FmEntry.Scalar -> {
                val v = e.value.trim()
                if (v.startsWith("[") && v.endsWith("]")) splitInline(v.substring(1, v.length - 1)).map { unquote(it) }
                else listOf(unquote(v))
            }
            null -> emptyList()
        }.filter { it.isNotBlank() }

    /** Splits an inline list on commas outside quotes. */
    private fun splitInline(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                quote == '"' && c == '\\' && i + 1 < s.length -> { cur.append(c).append(s[i + 1]); i++ }
                quote != null && c == quote -> { quote = null; cur.append(c) }
                quote == null && (c == '"' || c == '\'') -> { quote = c; cur.append(c) }
                quote == null && c == ',' -> { out += cur.toString().trim(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString().trim()
        return out
    }

    private fun unquote(s: String): String = when {
        s.length >= 2 && s.startsWith("\"") && s.endsWith("\"") ->
            s.substring(1, s.length - 1).replace(Regex("""\\(.)""")) { it.groupValues[1] }
        s.length >= 2 && s.startsWith("'") && s.endsWith("'") -> s.substring(1, s.length - 1).replace("''", "'")
        else -> s
    }

    /** Double-quoted, so `:`, `#`, `*` and a leading `'` stay text in YAML; `\` and `"` escaped. */
    private fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
