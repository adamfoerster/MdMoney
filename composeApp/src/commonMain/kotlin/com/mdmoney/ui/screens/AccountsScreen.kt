package com.mdmoney.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalStrings
import com.mdmoney.domain.Currency
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.UiState
import com.mdmoney.ui.components.CurrencyPicker
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.IndexRow
import com.mdmoney.ui.components.Meta
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.theme.LocalReinoColors

@Composable
fun AccountsScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    var showAdd by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("${s.appName} — Arquivo")
                    Text(
                        s.accounts,
                        style = MaterialTheme.typography.displayMedium,
                        color = reino.ink,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                ReinoButton(
                    text = s.settings,
                    onClick = { model.openSettings() },
                    variant = ReinoButtonVariant.Ghost,
                )
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                if (state.accounts.isEmpty()) {
                    Text(
                        s.noAccounts,
                        style = MaterialTheme.typography.bodyLarge,
                        color = reino.inkSoft,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                } else {
                    state.accounts.forEachIndexed { i, account ->
                        IndexRow(
                            number = (i + 1).toString().padStart(2, '0'),
                            title = account,
                            onClick = { model.openAccount(account) },
                            meta = { Meta(term = "Ver conta", count = null) },
                        )
                    }
                    HairlineDivider()
                }

                Spacer(Modifier.height(24.dp))
                ReinoButton(
                    text = s.addAccount,
                    onClick = { showAdd = true },
                    variant = ReinoButtonVariant.Secondary,
                    trailingArrow = true,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var currency by remember { mutableStateOf(Currency.NONE) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(s.addAccount, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(s.accountName) },
                    )
                    Text(
                        s.currency,
                        style = MaterialTheme.typography.labelLarge,
                        color = reino.inkSoft,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    CurrencyPicker(selected = currency, onSelect = { currency = it })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { model.createAccount(name, currency); showAdd = false }) {
                    Text(s.create)
                }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(s.cancel) } },
        )
    }
}
