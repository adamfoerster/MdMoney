package com.mdmoney.domain

/**
 * An Obsidian wikilink as a note's frontmatter carries it: `"[[casa|Casa]]"`.
 *
 * [target] is the note being pointed at — the identity the app keys on, the same way an account's
 * identity is its folder name. [title] is only how the link reads in Obsidian; it may be absent, and
 * it may be edited there without meaning anything to this app.
 */
data class VaultLink(val target: String, val title: String?)

/**
 * Reads a frontmatter value that should point at another note, tolerating everything a vault might
 * hold: `"[[casa|Casa]]"`, an unquoted `[[casa]]`, a full path (`[[categories/casa]]`), or the plain
 * `casa` that every note carried before links existed. Blank or absent yields null.
 *
 * Being tolerant here is what keeps an un-migrated vault readable: a note still saying `category:
 * casa` resolves to exactly the same category as one saying `"[[casa|Casa]]"`.
 */
fun parseVaultLink(raw: String?): VaultLink? {
    var text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null

    // Obsidian requires a link in a property to be quoted (bare `[[x]]` is a YAML flow sequence),
    // but a hand-written note may not be, so accept both.
    if (text.length >= 2 && (text.isWrappedIn('"') || text.isWrappedIn('\''))) {
        text = text.substring(1, text.length - 1).trim()
    }
    if (text.isEmpty()) return null

    if (!text.startsWith("[[") || !text.endsWith("]]")) return VaultLink(text, null)

    val inner = text.substring(2, text.length - 2)
    val pipe = inner.indexOf('|')
    // Obsidian may write a path when the name isn't unique; the note's own name is the identity.
    val target = (if (pipe < 0) inner else inner.substring(0, pipe)).trim().substringAfterLast('/')
    val title = if (pipe < 0) null else inner.substring(pipe + 1).trim().ifEmpty { null }
    return target.ifEmpty { null }?.let { VaultLink(it, title) }
}

/** Renders a link for the frontmatter, quoted so Obsidian reads it as a link and not as a list. */
fun formatVaultLink(target: String, title: String): String = "\"[[$target|$title]]\""

private fun String.isWrappedIn(quote: Char): Boolean = first() == quote && last() == quote
