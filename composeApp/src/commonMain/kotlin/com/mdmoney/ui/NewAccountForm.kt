package com.mdmoney.ui

import com.mdmoney.data.parseAmount

/**
 * The opening balance the new-account form describes: what was typed, signed by the *In credit* /
 * *Overdrawn* toggle. Blank (or not a number) is "set it later" — null — rather than zero, since an
 * account with no opening balance reads differently from one that opened at nothing.
 *
 * The toggle decides the sign, so a typed minus is ignored rather than doubling up with it.
 */
fun openingBalanceFrom(text: String, overdrawn: Boolean): Double? {
    val typed = parseAmount(text) ?: return null
    val magnitude = kotlin.math.abs(typed)
    // Never `-0.0`: it would print as "-0" in the note.
    return if (overdrawn && magnitude != 0.0) -magnitude else magnitude
}
