package com.mdmoney.domain

/**
 * An account's currency: the [symbol] that prefixes every figure shown for that account.
 *
 * A preset ([BRL], [EUR], [USD]) is stored as its [code]; a currency the user typed is stored as its
 * raw symbol verbatim; an account that has never chosen one is [NONE] and shows plain numbers, which
 * is exactly how the app read before currencies existed — so a vault written by an older build (or a
 * note the user keeps symbol-free in Obsidian) keeps reading unchanged.
 */
data class Currency(val code: String, val symbol: String) {

    /** A blank symbol means "no currency chosen" — figures print as bare numbers. */
    val hasSymbol: Boolean get() = symbol.isNotEmpty()

    val isCustom: Boolean get() = code == CUSTOM

    /**
     * What lands in the `currency:` frontmatter key: a preset's code, a custom's raw symbol, or
     * `null` to clear the key entirely (so [NONE] leaves the file exactly as it would have been
     * before this feature).
     */
    fun stored(): String? = when {
        !hasSymbol -> null
        isCustom -> symbol
        else -> code
    }

    companion object {
        const val CUSTOM = "CUSTOM"

        val NONE = Currency("NONE", "")
        val BRL = Currency("BRL", "R$")
        val EUR = Currency("EUR", "€")
        val USD = Currency("USD", "$")

        /** The presets offered in the picker, in the order they appear. */
        val PRESETS = listOf(BRL, EUR, USD)

        /**
         * Reads a stored `currency:` value: a known preset code (case-insensitive) maps to its
         * preset; any other non-blank value is a custom symbol carried as-is; blank/absent is [NONE].
         */
        fun fromStored(raw: String?): Currency {
            val t = raw?.trim().orEmpty()
            if (t.isEmpty()) return NONE
            PRESETS.firstOrNull { it.code.equals(t, ignoreCase = true) }?.let { return it }
            return Currency(CUSTOM, t)
        }

        /** Builds a custom currency from a user-typed symbol (blank collapses to [NONE]). */
        fun custom(symbol: String): Currency {
            val t = symbol.trim()
            return if (t.isEmpty()) NONE else Currency(CUSTOM, t)
        }
    }
}
