package com.mdmoney.data

import com.mdmoney.domain.Category
import com.mdmoney.domain.prettifySlug

/**
 * A category's own note, kept in the vault's `categories/` folder as `<slug>.md`:
 *
 * ```yaml
 * ---
 * type: category
 * title: Casa
 * description: Tudo o que a casa consome — água, luz, seguro, manutenção.
 * ---
 * ```
 *
 * Expense and ledger notes point here with `category: "[[casa|Casa]]"`, so a category is a real
 * place in the vault: it can be opened in Obsidian, described in prose, and backlinked from every
 * note that uses it.
 *
 * The app writes the note but never owns it. [title] and [description] are the user's to edit — in
 * Obsidian or anywhere else — and unknown keys and the body survive a round-trip, exactly as in
 * [AccountMeta] and expense notes.
 */
class CategoryMeta private constructor(
    val slug: String,
    private val note: MarkdownNote,
) {
    /** Falls back to a readable form of the slug, so a note with no title still reads properly. */
    val title: String get() = note.scalar(KEY_TITLE) ?: prettifySlug(slug)

    val description: String? get() = note.scalar(KEY_DESCRIPTION)

    fun toCategory(): Category = Category(slug = slug, title = title, description = description)

    fun serialize(): String = FrontmatterParser.serialize(note)

    companion object {
        /** The vault-root folder holding category notes. Not an account — see [VaultRepository]. */
        const val FOLDER = "categories"

        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_TYPE = "type"
        private const val TYPE_CATEGORY = "category"

        fun read(slug: String, content: String?): CategoryMeta {
            val note = content?.let { FrontmatterParser.parse(it) } ?: MarkdownNote(mutableListOf(), "")
            return CategoryMeta(slug, note)
        }

        /**
         * A brand-new category note. [description] is written as a bare `description:` when unknown
         * rather than omitted, so the field is visible and waiting in Obsidian's property editor.
         */
        fun create(slug: String, title: String = prettifySlug(slug), description: String? = null): CategoryMeta {
            val note = MarkdownNote(mutableListOf(), "")
            note.setScalar(KEY_TYPE, TYPE_CATEGORY)
            note.setScalar(KEY_TITLE, title)
            note.setScalar(KEY_DESCRIPTION, description)
            return CategoryMeta(slug, note)
        }
    }
}
