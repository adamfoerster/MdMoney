package com.mdmoney

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mdmoney.platform.JvmAppSettings
import com.mdmoney.platform.JvmImportPlatform
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import com.mdmoney.resources.Res
import com.mdmoney.resources.app_icon
import java.io.File
import kotlin.system.exitProcess
import org.jetbrains.compose.resources.painterResource

fun main() {
    application { Main() }
    // llama.cpp (statement import) leaves non-daemon threads running; without this the process
    // outlives its window and keeps the engine's library loaded.
    exitProcess(0)
}

@Composable
private fun ApplicationScope.Main() {
    val prefs = JvmPrefs()
    val storage = JvmVaultStorage(prefs)
    val settings = JvmAppSettings(prefs)
    val dbPath = File(System.getProperty("user.home"), ".mdmoney/cache.db")
        .also { it.parentFile?.mkdirs() }.absolutePath
    val importPlatform = remember { JvmImportPlatform() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "MdMoney",
        icon = painterResource(Res.drawable.app_icon),
    ) {
        App(storage, settings, dbPath, importPlatform = importPlatform)
    }
}
