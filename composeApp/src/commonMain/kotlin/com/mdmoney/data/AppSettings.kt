package com.mdmoney.data

/** Small persisted preferences that aren't tied to the vault contents. */
interface AppSettings {
    /** Stored language override (ISO code), or null to follow the system language. */
    fun language(): String?

    fun setLanguage(code: String?)

    /** Stored decimal-separator code ([DecimalSeparator.code]), or null to use the default (dot). */
    fun decimalSeparator(): String?

    fun setDecimalSeparator(code: String?)
}
