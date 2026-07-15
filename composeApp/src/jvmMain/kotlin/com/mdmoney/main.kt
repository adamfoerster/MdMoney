package com.mdmoney

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mdmoney.platform.JvmAppSettings
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import java.io.File

fun main() = application {
    val prefs = JvmPrefs()
    val storage = JvmVaultStorage(prefs)
    val settings = JvmAppSettings(prefs)
    val dbPath = File(System.getProperty("user.home"), ".mdmoney/cache.db")
        .also { it.parentFile?.mkdirs() }.absolutePath
    Window(
        onCloseRequest = ::exitApplication,
        title = "MdMoney",
    ) {
        App(storage, settings, dbPath)
    }
}
