package com.mdmoney.data

/**
 * The per-account metadata note kept at the vault **root** (`<vault>/<Account>.md`). Its frontmatter
 * carries the account's opening balance for each year under flat `initial-<year>` keys, e.g.:
 *
 * ```yaml
 * type: account
 * account: Regions
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
    fun initialBalance(year: Int): Double? = parseAmount(note.scalar(key(year)))

    /** Returns this same instance with the year's opening balance set (or cleared when null). */
    fun withInitialBalance(year: Int, value: Double?): AccountMeta {
        if (!note.hasKey("type")) note.setScalar("type", "account")
        note.setScalar("account", account)
        if (value == null) note.removeKey(key(year)) else note.setScalar(key(year), formatAmount(value))
        return this
    }

    fun serialize(): String = FrontmatterParser.serialize(note)

    companion object {
        private fun key(year: Int) = "initial-$year"

        fun read(account: String, content: String?): AccountMeta {
            val note = content?.let { FrontmatterParser.parse(it) } ?: MarkdownNote(mutableListOf(), "")
            return AccountMeta(account, note)
        }
    }
}
