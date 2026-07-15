package com.mdmoney.platform

import java.io.File
import java.util.Properties

/** Tiny properties-file store under ~/.mdmoney used by the desktop build for the vault path + language. */
class JvmPrefs {
    private val file = File(System.getProperty("user.home"), ".mdmoney/settings.properties")
    private val props = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    fun get(key: String): String? = props.getProperty(key)?.takeIf { it.isNotBlank() }

    fun set(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "MdMoney") }
    }
}
