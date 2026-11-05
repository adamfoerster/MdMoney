package com.mdmoney.data

import com.mdmoney.domain.Currency

/**
 * The per-account metadata note kept at the vault **root** (`<vault>/<Account>.md`). Its frontmatter
 * carries the account's opening balance for each year under flat `initial-<year>` keys, plus the
 * currency its figures are shown in, e.g.:
 *
 * ```yaml
 * type: account
 * account: nubank
 * title: Nubank
 * currency: BRL
 * initial-2026: 1200
 * ```
 *
 * Wraps a [MarkdownNote] so unknown keys and the body survive a round-trip, exactly like [Expense]
 * notes.
 */
class AccountMeta private constructor(
    val account: String,
    private val note: MarkdownNote,
) {
    /**
     * How the account reads on screen and in the `account: "[[nubank|Nubank]]"` links pointing here.
     *
     * Only presentation: the folder name remains the account's identity, which is why this is free
     * to differ from it in casing or wording.
     */
    val title: String get() = note.scalar("title") ?: account

    fun initialBalance(year: Int): Double? = parseAmount(note.scalar(key(year)))

    /** The account's currency; [Currency.NONE] when the note carries no `currency:` key. */
    val currency: Currency get() = Currency.fromStored(note.scalar(CURRENCY_KEY))

    /** Returns this same instance with the currency set (or the key cleared for [Currency.NONE]). */
    fun withCurrency(currency: Currency): AccountMeta {
        if (!note.hasKey("type")) note.setScalar("type", "account")
        note.setScalar("account", account)
        val stored = currency.stored()
        if (stored == null) note.removeKey(CURRENCY_KEY) else note.setScalar(CURRENCY_KEY, stored, after = "title")
        return this
    }

    /** Returns this same instance with the display title set. */
    fun withTitle(title: String): AccountMeta {
        if (!note.hasKey("type")) note.setScalar("type", "account")
        note.setScalar("account", account)
        note.setScalar("title", title, after = "account")
        return this
    }

    /** Returns this same instance with the year's opening balance set (or cleared when null). */
    fun withInitialBalance(year: Int, value: Double?): AccountMeta {
        if (!note.hasKey("type")) note.setScalar("type", "account")
        note.setScalar("account", account)
        if (value == null) note.removeKey(key(year)) else note.setScalar(key(year), formatAmount(value))
        return this
    }

    /** The statements imported into this account, newest first. */
    val imports: List<ImportRecord>
        get() = importLines().mapNotNull { ImportRecord.decode(it) }

    /**
     * Returns this same instance with [record] on top of the `imports:` list. Importing a file of the
     * same name again replaces its earlier entry rather than listing it twice, the list is capped at
     * [MAX_IMPORTS], and lines this app does not understand are kept as they were.
     */
    fun withImport(record: ImportRecord): AccountMeta {
        if (!note.hasKey("type")) note.setScalar("type", "account")
        note.setScalar("account", account)
        val kept = importLines().filter { ImportRecord.decode(it)?.fileName != record.fileName }
        val lines = listOf(itemLine(record)) + kept.map { "  - ${it.trim().removePrefix("-").trim()}" }
        val entry = FmEntry.Raw(IMPORTS_KEY, listOf("$IMPORTS_KEY:") + lines.take(MAX_IMPORTS))
        val idx = note.indexOfKey(IMPORTS_KEY)
        if (idx >= 0) note.entries[idx] = entry else note.entries.add(entry)
        return this
    }

    private fun itemLine(record: ImportRecord) = "  - \"${record.encode()}\""

    /** The raw items of the `imports:` block, without the key line. */
    private fun importLines(): List<String> =
        (note.entries.firstOrNull { it.key == IMPORTS_KEY } as? FmEntry.Raw)?.lines?.drop(1).orEmpty()

    fun serialize(): String = FrontmatterParser.serialize(note)

    companion object {
        private const val CURRENCY_KEY = "currency"
        private const val IMPORTS_KEY = "imports"

        /** How many past imports the note remembers. */
        const val MAX_IMPORTS = 20
        private fun key(year: Int) = "initial-$year"

        fun read(account: String, content: String?): AccountMeta {
            val note = content?.let { FrontmatterParser.parse(it) } ?: MarkdownNote(mutableListOf(), "")
            return AccountMeta(account, note)
        }
    }
}
