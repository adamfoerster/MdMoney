package com.mdmoney.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalStrings
import com.mdmoney.data.DecimalSeparator
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.UiState
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.i18n.Language
import com.mdmoney.ui.theme.LocalReinoColors

@Composable
fun SettingsScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            ReinoButton("‹ ${s.accounts}", onClick = { model.back() }, variant = ReinoButtonVariant.Ghost)
            Text(
                s.settings,
                style = MaterialTheme.typography.displayMedium,
                color = reino.ink,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )

            Eyebrow(s.language)
            Spacer(Modifier.height(8.dp))
            OptionRow(s.systemDefault, state.followSystem) { model.setLanguage(null) }
            Language.entries.forEach { lang ->
                OptionRow(lang.displayName, !state.followSystem && state.language == lang) { model.setLanguage(lang) }
            }

            Spacer(Modifier.height(24.dp))
            HairlineDivider()
            Spacer(Modifier.height(24.dp))

            Eyebrow(s.decimalSeparator)
            Spacer(Modifier.height(8.dp))
            OptionRow(s.separatorDot, state.decimalSeparator == DecimalSeparator.DOT) {
                model.setDecimalSeparator(DecimalSeparator.DOT)
            }
            OptionRow(s.separatorComma, state.decimalSeparator == DecimalSeparator.COMMA) {
                model.setDecimalSeparator(DecimalSeparator.COMMA)
            }

            Spacer(Modifier.height(24.dp))
            HairlineDivider()
            Spacer(Modifier.height(24.dp))

            Eyebrow(s.changeVault)
            Text(
                state.vaultLabel ?: s.vaultNotSelected,
                style = MaterialTheme.typography.bodyMedium,
                color = reino.inkSoft,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            ReinoButton(s.changeVault, onClick = { model.pickVault() }, variant = ReinoButtonVariant.Secondary)
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val reino = LocalReinoColors.current
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = reino.brass, unselectedColor = reino.inkFaint),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = reino.ink,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
