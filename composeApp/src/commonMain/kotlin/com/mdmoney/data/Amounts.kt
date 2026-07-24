package com.mdmoney.data

import kotlin.math.abs
import kotlin.math.round

/**
 * Formats a monetary amount for the frontmatter, using at most two decimals and trimming trailing
 * zeros (`200`, `1232.5`, `92.62`). Implemented via integer cents so the output is identical on
 * JVM, Android, and Kotlin/Native rather than relying on `Double.toString()`.
 */
fun formatAmount(value: Double): String {
    val negative = value < 0
    val cents = round(abs(value) * 100.0).toLong()
    val whole = cents / 100
    val rem = cents % 100
    val sign = if (negative && cents != 0L) "-" else ""
    return when {
        rem == 0L -> "$sign$whole"
        rem % 10 == 0L -> "$sign$whole.${rem / 10}"
        else -> "$sign$whole.${rem.toString().padStart(2, '0')}"
    }
}

/**
 * Formats an amount for **display**, always with exactly two decimals and the given [separator]
 * (`.` or `,`), e.g. `1234,50` vs `1234.50`. When a [symbol] is given (the account's currency) it is
 * prefixed after the sign — `-R$ 1234,50`. Distinct from [formatAmount], which is for the frontmatter
 * and trims trailing zeros; neither the display separator nor the symbol must ever leak into a file.
 */
fun formatMoney(value: Double, separator: Char, symbol: String = ""): String {
    val negative = value < 0
    val cents = round(abs(value) * 100.0).toLong()
    val whole = cents / 100
    val rem = cents % 100
    val sign = if (negative && cents != 0L) "-" else ""
    // A non-breaking space keeps the symbol on the same line as its figure inside tight columns.
    val prefix = if (symbol.isEmpty()) "" else "$symbol "
    return "$sign$prefix$whole$separator${rem.toString().padStart(2, '0')}"
}

/**
 * Formats an amount to pre-fill an **editable** field: natural precision (no forced trailing zeros)
 * but honoring the chosen display [separator], so typing stays clean while staying consistent.
 */
fun formatInput(value: Double, separator: Char): String =
    if (separator == '.') formatAmount(value) else formatAmount(value).replace('.', separator)

/** Parses a frontmatter amount, tolerating a comma decimal separator. Blank/absent yields null. */
fun parseAmount(raw: String?): Double? {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return null
    return t.replace(",", ".").toDoubleOrNull()
}
