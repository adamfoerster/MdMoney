package com.mdmoney.domain

/**
 * A spending category — one note in the vault's `categories/` folder that expenses and ledgers link
 * to, so a category has somewhere to say what it means.
 *
 * [slug] is the note's file name, and like an account's folder it *is* the identity: it's what the
 * links point at and what the app groups by. [title] is how it reads on screen and in Obsidian, and
 * may be edited freely without breaking a single link.
 */
data class Category(
    val slug: String,
    val title: String,
    val description: String? = null,
)

/**
 * How a [slug] should read on screen, given the category notes [categories] the vault holds.
 *
 * The title always comes from the note rather than from the link text an expense carries, so
 * renaming a category in Obsidian renames it everywhere at once without rewriting a single expense.
 */
fun categoryTitle(slug: String?, categories: List<Category>): String? =
    slug?.takeIf { it.isNotBlank() }
        ?.let { s -> categories.firstOrNull { it.slug == s }?.title ?: prettifySlug(s) }

/** `adam shopping` -> `Adam Shopping`: a readable title for a category whose note says nothing. */
fun prettifySlug(slug: String): String =
    slug.split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
        .ifBlank { slug }

/**
 * Strips what a slug cannot hold; accents and spaces are kept as typed.
 *
 * Beyond the characters no file name allows, `[]#^` would break the `[[link]]` that points at the
 * note — inside a wikilink they mean bracket, heading and block.
 */
fun sanitizeSlug(text: String): String =
    text.trim().replace(Regex("""[\\/:*?"<>|\[\]#^]"""), "-").trim(' ', '-')

/**
 * Resolves what someone typed into a category field onto a category's [Category.slug].
 *
 * The field shows titles, but a note links to the *slug* — so typing "Casa" when `casa` already
 * exists must land on `casa` rather than quietly forking a second category note that means the same
 * thing. Anything genuinely new becomes a slug of its own.
 */
fun resolveCategorySlug(typed: String, known: List<Category>): String? {
    val text = typed.trim()
    if (text.isEmpty()) return null
    known.firstOrNull { it.title.equals(text, ignoreCase = true) }?.let { return it.slug }
    known.firstOrNull { it.slug.equals(text, ignoreCase = true) }?.let { return it.slug }
    return sanitizeSlug(text).ifEmpty { null }
}
