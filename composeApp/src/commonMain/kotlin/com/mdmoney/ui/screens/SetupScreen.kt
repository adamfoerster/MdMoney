package com.mdmoney.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalStrings
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.UiState
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.theme.LocalReinoColors

@Composable
fun SetupScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Eyebrow("Est. — Controle Financeiro")
            Text(
                s.appName,
                style = MaterialTheme.typography.displayLarge,
                color = reino.ink,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                s.vaultNotSelectedHint,
                style = MaterialTheme.typography.bodyLarge,
                color = reino.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp, bottom = 32.dp),
            )
            ReinoButton(text = s.selectVault, onClick = { model.pickVault() }, trailingArrow = true)
        }
    }
}
