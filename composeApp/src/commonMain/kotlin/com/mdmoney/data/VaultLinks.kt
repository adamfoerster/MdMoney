package com.mdmoney.data

import com.mdmoney.domain.Category
import com.mdmoney.domain.formatVaultLink
import com.mdmoney.domain.parseVaultLink
import com.mdmoney.domain.prettifySlug

/**
 * The display titles the app puts on the wikilinks it writes (`category: "[[casa|Casa]]"`).
 *
 * A link's *target* is the identity — the category note's file name, the account's folder — and it
 * is the only half the app reads back. The title half is presentation, and it lives in the metadata
 * note being pointed at, so a writer needs this to look one up.
 *
 * Nothing here can fail: a target with no metadata note yet still gets a readable title derived from
 * itself, which is what lets a note be written before its category note exists.
 */
class VaultLinks(
    private val categoryTitles: Map<String, String> = emptyMap(),
    private val accountTitles: Map<String, String> = emptyMap(),
) {
    fun category(slug: String): String = formatVaultLink(slug, categoryTitles[slug] ?: prettifySlug(slug))

    fun account(folder: String): String = formatVaultLink(folder, accountTitles[folder] ?: folder)

    companion object {
        /** Titles fall back to the targets themselves — enough to write a correct link. */
        val Empty = VaultLinks()

        fun of(categories: List<Category>, accountTitles: Map<String, String>) = VaultLinks(
            categoryTitles = categories.associate { it.slug to it.title },
            accountTitles = accountTitles,
        )
    }
}

/**
 * Points [key] at [target] as a wikilink — or clears it to a bare `key:` when there is no target.
 *
 * A link that already resolves to [target] is left exactly as written, which does two things: a
 * title tuned in Obsidian (`"[[casa|Nossa casa]]"`) is never stamped back over, and rewriting a
 * whole vault becomes idempotent — the second run changes nothing. A value that is merely a plain
 * legacy name (`casa`, no link at all) is upgraded, since that is the migration.
 */
internal fun MarkdownNote.setLink(
    key: String,
    target: String?,
    after: String? = null,
    render: (String) -> String,
) {
    if (target == null) {
        if (hasKey(key)) setScalar(key, null)
        return
    }
    val current = parseVaultLink(scalar(key))
    if (current?.target == target && current.title != null) return
    setScalar(key, render(target), after = after)
}
