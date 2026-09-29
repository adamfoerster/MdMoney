package com.mdmoney.platform

import com.mdmoney.data.AppSettings

class JvmAppSettings(private val prefs: JvmPrefs) : AppSettings {
    override fun language(): String? = prefs.get(KEY_LANG)
    override fun setLanguage(code: String?) = prefs.set(KEY_LANG, code)

    override fun decimalSeparator(): String? = prefs.get(KEY_DECIMAL)
    override fun setDecimalSeparator(code: String?) = prefs.set(KEY_DECIMAL, code)

    override fun importModel(): String? = prefs.get(KEY_IMPORT_MODEL)
    override fun setImportModel(value: String?) = prefs.set(KEY_IMPORT_MODEL, value)

    private companion object {
        const val KEY_LANG = "language"
        const val KEY_DECIMAL = "decimalSeparator"
        const val KEY_IMPORT_MODEL = "importModel"
    }
}
