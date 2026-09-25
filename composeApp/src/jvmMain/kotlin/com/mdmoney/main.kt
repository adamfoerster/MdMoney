package com.mdmoney

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mdmoney.platform.JvmAppSettings
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import com.mdmoney.resources.Res
import com.mdmoney.resources.app_icon
import java.io.File
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    val prefs = JvmPrefs()
    val storage = JvmVaultStorage(prefs)
    val settings = JvmAppSettings(prefs)
    val dbPath = File(System.getProperty("user.home"), ".mdmoney/cache.db")
        .also { it.parentFile?.mkdirs() }.absolutePath
    Window(
        onCloseRequest = ::exitApplication,
        title = "MdMoney",
        icon = painterResource(Res.drawable.app_icon),
    ) {
        App(storage, settings, dbPath)
    }
}
