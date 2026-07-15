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

    private companion object {
        const val KEY_LANG = "language"
        const val KEY_DECIMAL = "decimalSeparator"
    }
}
