package com.mdmoney

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.mdmoney.data.AppSettings
import com.mdmoney.data.VaultStorage
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.HomeTab
import com.mdmoney.ui.MdMoneyTheme
import com.mdmoney.ui.Screen
import com.mdmoney.ui.components.ReinoTabBar
import com.mdmoney.ui.i18n.EnStrings
import com.mdmoney.ui.i18n.Strings
import com.mdmoney.ui.i18n.stringsFor
import com.mdmoney.ui.screens.AccountGridScreen
import com.mdmoney.ui.screens.AccountsScreen
import com.mdmoney.ui.screens.EditExpenseSheet
import com.mdmoney.ui.screens.HomeScreen
import com.mdmoney.ui.screens.LedgerSheet
import com.mdmoney.ui.screens.OneOffSheet
import com.mdmoney.ui.screens.ReportsScreen
import com.mdmoney.ui.screens.SettingsScreen
import com.mdmoney.ui.screens.SetupScreen

/** Provides the current language's strings to the whole tree. */
val LocalStrings = staticCompositionLocalOf<Strings> { EnStrings }

/** The cents separator character (`.` or `,`) for displaying and editing amounts. */
val LocalDecimalSeparator = staticCompositionLocalOf { '.' }

@Composable
fun App(storage: VaultStorage, settings: AppSettings, dbPath: String, initialAccount: String? = null) {
    val scope = rememberCoroutineScope()
    val model = remember { AppModel(storage, settings, scope, dbPath, initialAccount) }
    val state by model.state.collectAsState()

    MdMoneyTheme {
        CompositionLocalProvider(
            LocalStrings provides stringsFor(state.language),
            LocalDecimalSeparator provides state.decimalSeparator.char,
        ) {
            // Paper extends edge-to-edge (behind the status bar / home indicator); content is inset.
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    when (state.screen) {
                        Screen.Setup -> SetupScreen(model, state)
                        Screen.Settings -> SettingsScreen(model, state)
                        Screen.Accounts -> AccountsScreen(model, state)
                        Screen.AccountShell -> Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (state.tab) {
                                    HomeTab.HOME -> HomeScreen(model, state)
                                    HomeTab.ANNUAL -> AccountGridScreen(model, state)
                                    HomeTab.REPORTS -> ReportsScreen()
                                    HomeTab.SETTINGS -> SettingsScreen(model, state)
                                }
                            }
                            ReinoTabBar(current = state.tab, onSelect = { model.selectTab(it) })
                        }
                    }
                    state.editor?.let { EditExpenseSheet(model, it) }
                    state.oneOff?.let { OneOffSheet(model, it) }
                    state.ledger?.let { LedgerSheet(model, it) }
                }
            }
        }
    }
}
