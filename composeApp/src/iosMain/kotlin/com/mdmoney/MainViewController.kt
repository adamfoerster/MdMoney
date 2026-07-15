package com.mdmoney

import androidx.compose.ui.window.ComposeUIViewController
import com.mdmoney.platform.IosAppSettings
import com.mdmoney.platform.IosVaultStorage
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

fun MainViewController() = ComposeUIViewController {
    val initialAccount = NSUserDefaults.standardUserDefaults.stringForKey("start.account")
    val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
    val dbPath = (docs ?: NSTemporaryDirectory().trimEnd('/')) + "/mdmoney-cache.db"
    App(
        storage = IosVaultStorage(),
        settings = IosAppSettings(),
        dbPath = dbPath,
        initialAccount = initialAccount,
    )
}
