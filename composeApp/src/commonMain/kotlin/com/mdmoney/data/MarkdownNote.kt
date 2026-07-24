package com.mdmoney.data

/**
 * A parsed markdown note: an ordered list of YAML frontmatter entries plus the raw body that
 * follows the closing `---`.
 *
 * The point of this type is round-trip fidelity. Entries the app doesn't understand (e.g.
 * `renewal_date`, `billing_cycle`, `tags`) are kept verbatim and in their original position, and
 * the body is preserved exactly. Only the scalar keys the app manages are rewritten on save.
 */
class MarkdownNote(
    val entries: MutableList<FmEntry>,
    val body: String,
) {
    fun indexOfKey(key: String): Int = entries.indexOfFirst { it.key == key }

    fun hasKey(key: String): Boolean = indexOfKey(key) >= 0

    /** Trimmed scalar value for [key], or null when the key is absent or holds an empty/blank value. */
    fun scalar(key: String): String? {
        val e = entries.firstOrNull { it.key == key } as? FmEntry.Scalar ?: return null
        return e.value.trim().ifEmpty { null }
    }

    /**
     * Sets [key] to [value] (empty/null renders as a bare `key:`). If [key] is absent, the first
     * present alias in [aliases] is renamed in place to [key] (preserving position); any remaining
     * alias entries are dropped.
     *
     * When nothing matches, the entry is added straight after [after] when that key is present, and
     * appended otherwise. These are notes people read in Obsidian, so a key added to an existing
     * note should land beside its relatives rather than below twelve months of amounts.
     */
    fun setScalar(key: String, value: String?, aliases: List<String> = emptyList(), after: String? = null) {
        val rendered = value?.trim().orEmpty()
        val allKeys = listOf(key) + aliases
        val idx = entries.indexOfFirst { it.key in allKeys }
        if (idx >= 0) {
            entries[idx] = FmEntry.Scalar(key, rendered)
            // Drop any duplicate alias entries left over (e.g. a stray legacy `fev`).
            for (i in entries.indices.reversed()) {
                if (i != idx && entries[i].key in allKeys) entries.removeAt(i)
            }
            return
        }
        val anchor = after?.let { indexOfKey(it) }?.takeIf { it >= 0 }
        if (anchor == null) entries.add(FmEntry.Scalar(key, rendered))
        else entries.add(anchor + 1, FmEntry.Scalar(key, rendered))
    }

    fun removeKey(key: String) {
        entries.removeAll { it.key == key }
    }
}

/** A single logical frontmatter entry. */
sealed interface FmEntry {
    val key: String

    /** `key: value` (value may be empty). */
    data class Scalar(override val key: String, val value: String) : FmEntry

    /** A key whose value spans indented continuation lines (e.g. a block list). Kept verbatim. */
    data class Raw(override val key: String, val lines: List<String>) : FmEntry
}
