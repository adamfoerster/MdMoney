package com.mdmoney.platform

import com.mdmoney.data.AppSettings

class JvmAppSettings(private val prefs: JvmPrefs) : AppSettings {
    override fun language(): String? = prefs.get(KEY_LANG)
    override fun setLanguage(code: String?) = prefs.set(KEY_LANG, code)

    override fun decimalSeparator(): String? = prefs.get(KEY_DECIMAL)
    override fun setDecimalSeparator(code: String?) = prefs.set(KEY_DECIMAL, code)

    private companion object {
        const val KEY_LANG = "language"
        const val KEY_DECIMAL = "decimalSeparator"
    }
}
