package com.mdmoney.platform

import android.content.Context
import com.mdmoney.data.AppSettings

class AndroidAppSettings(context: Context) : AppSettings {
    private val prefs = context.getSharedPreferences("mdmoney", Context.MODE_PRIVATE)

    override fun language(): String? = prefs.getString(KEY_LANG, null)?.takeIf { it.isNotBlank() }

    override fun setLanguage(code: String?) {
        prefs.edit().apply {
            if (code == null) remove(KEY_LANG) else putString(KEY_LANG, code)
        }.apply()
    }

    override fun decimalSeparator(): String? =
        prefs.getString(KEY_DECIMAL, null)?.takeIf { it.isNotBlank() }

    override fun setDecimalSeparator(code: String?) {
        prefs.edit().apply {
            if (code == null) remove(KEY_DECIMAL) else putString(KEY_DECIMAL, code)
        }.apply()
    }

    private companion object {
        const val KEY_LANG = "language"
        const val KEY_DECIMAL = "decimalSeparator"
    }
}
