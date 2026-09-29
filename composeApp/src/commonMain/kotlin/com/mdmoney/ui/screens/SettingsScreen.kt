package com.mdmoney.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import com.mdmoney.AppVersion
import com.mdmoney.importer.ImportModelPreset
import com.mdmoney.ui.ModelStatus
import com.mdmoney.ui.StatementImportModel
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mdmoney.ui.theme.LocalReinoColors

@Composable
fun SettingsScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current

    Surface(Modifier.fillMaxSize()) {
        // The options plus the vault section and version run past a short screen; without a scroll
        // the bottom is simply clipped and unreachable.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
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

            model.statementImport?.let { importer ->
                ImportModelSettings(importer)
                Spacer(Modifier.height(24.dp))
                HairlineDivider()
                Spacer(Modifier.height(24.dp))
            }

            Eyebrow(s.changeVault)
            Text(
                state.vaultLabel ?: s.vaultNotSelected,
                style = MaterialTheme.typography.bodyMedium,
                color = reino.inkSoft,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            ReinoButton(s.changeVault, onClick = { model.pickVault() }, variant = ReinoButtonVariant.Secondary)

            Spacer(Modifier.height(24.dp))
            HairlineDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                "${s.appName} ${AppVersion.VERSION}",
                style = MaterialTheme.typography.labelSmall,
                color = reino.inkFaint,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/** Choosing, downloading and removing the statement-import model. */
@Composable
private fun ImportModelSettings(importer: StatementImportModel) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val state by importer.state.collectAsState()
    val status = state.modelStatus

    Eyebrow(s.importModelSection)
    Text(
        s.importModelHint,
        style = MaterialTheme.typography.bodySmall,
        color = reino.inkSoft,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    if (status == ModelStatus.Unsupported) {
        Text(s.modelUnsupported, style = MaterialTheme.typography.bodyMedium, color = reino.brassDeep)
        return
    }
    OptionRow("${s.modelLight} · ${gigabytes(ImportModelPreset.LIGHT.sizeBytes)}", state.model.preset == ImportModelPreset.LIGHT) {
        importer.choosePreset(ImportModelPreset.LIGHT)
    }
    OptionRow("${s.modelAccurate} · ${gigabytes(ImportModelPreset.ACCURATE.sizeBytes)}", state.model.preset == ImportModelPreset.ACCURATE) {
        importer.choosePreset(ImportModelPreset.ACCURATE)
    }
    state.model.customPath?.let { path ->
        OptionRow("${s.modelCustom}: ${path.substringAfterLast('/').substringAfterLast('\\')}", true) {}
    }
    Text(
        when (status) {
            ModelStatus.Ready -> s.modelReady
            ModelStatus.Missing -> s.modelMissing
            ModelStatus.Verifying -> s.modelVerifying
            is ModelStatus.Downloading -> s.modelDownloading(percent(status.done, status.total))
            is ModelStatus.Failed -> "${s.modelFailed} (${status.reason})"
            ModelStatus.Unsupported -> s.modelUnsupported
        },
        style = MaterialTheme.typography.labelMedium,
        color = if (status is ModelStatus.Failed) reino.brassDeep else reino.inkSoft,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    if (status is ModelStatus.Downloading) {
        LinearProgressIndicator(
            progress = { if (status.total > 0) status.done.toFloat() / status.total else 0f },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            color = reino.brass,
        )
        ReinoButton(s.cancelDownload, onClick = { importer.cancelDownload() }, variant = ReinoButtonVariant.Secondary)
    } else if (state.model.preset != null) {
        if (status == ModelStatus.Ready) {
            ReinoButton(s.removeModel, onClick = { importer.removeModel() }, variant = ReinoButtonVariant.Secondary)
        } else if (status != ModelStatus.Verifying) {
            ReinoButton(s.downloadModel, onClick = { importer.downloadModel() }, trailingArrow = true)
        }
    }
    if (importer.canPickModelFile) {
        Spacer(Modifier.height(8.dp))
        ReinoButton(s.chooseModelFile, onClick = { importer.chooseModelFile() }, variant = ReinoButtonVariant.Ghost)
    }
}

/** `1117320736` -> `1.1 GB`: sizes the user weighs before a download, not exact byte counts. */
internal fun gigabytes(bytes: Long): String {
    val tenths = (bytes + 50_000_000) / 100_000_000
    return "${tenths / 10}.${tenths % 10} GB"
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
