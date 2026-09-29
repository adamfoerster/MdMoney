package com.mdmoney.platform

import com.mdmoney.data.AppSettings
import platform.Foundation.NSUserDefaults

class IosAppSettings : AppSettings {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun language(): String? = defaults.stringForKey(KEY_LANG)?.takeIf { it.isNotBlank() }

    override fun setLanguage(code: String?) {
        if (code == null) defaults.removeObjectForKey(KEY_LANG) else defaults.setObject(code, KEY_LANG)
    }

    override fun decimalSeparator(): String? = defaults.stringForKey(KEY_DECIMAL)?.takeIf { it.isNotBlank() }

    override fun setDecimalSeparator(code: String?) {
        if (code == null) defaults.removeObjectForKey(KEY_DECIMAL) else defaults.setObject(code, KEY_DECIMAL)
    }

    override fun importModel(): String? = defaults.stringForKey(KEY_IMPORT_MODEL)?.takeIf { it.isNotBlank() }

    override fun setImportModel(value: String?) {
        if (value == null) defaults.removeObjectForKey(KEY_IMPORT_MODEL) else defaults.setObject(value, KEY_IMPORT_MODEL)
    }

    private companion object {
        const val KEY_LANG = "language"
        const val KEY_DECIMAL = "decimalSeparator"
        const val KEY_IMPORT_MODEL = "importModel"
    }
}
