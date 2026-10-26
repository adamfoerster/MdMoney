package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.box
import com.mdmoney.ui.theme.importCard
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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

    Surface(Modifier.fillMaxSize(), color = D.bg) {
        // The options plus the vault section and version run past a short screen; without a scroll
        // the bottom is simply clipped and unreachable.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                ReinoButton("‹ ${s.accounts}", onClick = { model.back() }, variant = ReinoButtonVariant.Ghost)
                Text(
                    s.settings,
                    style = MaterialTheme.typography.displayMedium,
                    color = D.ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Section(s.language) {
                Column(Modifier.fillMaxWidth().importCard()) {
                    ChoiceRow(s.systemDefault, state.followSystem, first = true) { model.setLanguage(null) }
                    Language.entries.forEach { lang ->
                        ChoiceRow(lang.displayName, !state.followSystem && state.language == lang) { model.setLanguage(lang) }
                    }
                }
            }

            Section(s.decimalSeparator) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.bar).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Segment(s.separatorDot, state.decimalSeparator == DecimalSeparator.DOT, Modifier.weight(1f)) {
                        model.setDecimalSeparator(DecimalSeparator.DOT)
                    }
                    Segment(s.separatorComma, state.decimalSeparator == DecimalSeparator.COMMA, Modifier.weight(1f)) {
                        model.setDecimalSeparator(DecimalSeparator.COMMA)
                    }
                }
            }

            model.statementImport?.let { importer -> ImportModelSettings(importer) }

            Section(s.changeVault) {
                Row(
                    Modifier.fillMaxWidth().importCard().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        state.vaultLabel ?: s.vaultNotSelected,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = D.soft,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.height(40.dp).box(D.field, Color(0xFFB8AE9A), 8).clickable { model.pickVault() }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(s.changeVault, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = D.ink)
                    }
                }
            }

            Text(
                "${s.appName} ${AppVersion.VERSION}".uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = D.raw,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = D.soft,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

/** A row of a list card that is either the chosen option (✓) or a way to choose it. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, first: Boolean = false, onSelect: () -> Unit) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onSelect).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = D.ink,
            modifier = Modifier.weight(1f),
        )
        if (selected) Text("✓", style = MaterialTheme.typography.bodyLarge, color = D.teal)
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) D.card else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) D.ink else D.soft,
        )
    }
}

/** A selectable card for one model preset: name and size, and the model's state when it is the chosen one. */
@Composable
private fun ModelTile(name: String, size: String, selected: Boolean, status: String?, statusColor: Color, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) D.card else D.field)
            .border(if (selected) 2.dp else 1.dp, if (selected) D.ink else D.fieldBorder, shape)
            .clickable(onClick = onSelect).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium, color = D.ink, modifier = Modifier.weight(1f))
            Text(size, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = D.soft)
        }
        if (selected && status != null) {
            Text(status, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), fontWeight = FontWeight.Medium, color = statusColor)
        }
    }
}

@Composable
private fun ActionRow(label: String, color: Color, first: Boolean = false, trailing: String? = null, onClick: () -> Unit) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = color, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = color) }
    }
}

/** Choosing, downloading and removing the statement-import model. */
@Composable
private fun ImportModelSettings(importer: StatementImportModel) {
    val s = LocalStrings.current
    val state by importer.state.collectAsState()
    val status = state.modelStatus

    Section(s.importModelSection) {
        Column(Modifier.fillMaxWidth().importCard().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                s.importModelHint,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF23473F),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.tagIncome).padding(horizontal = 12.dp, vertical = 10.dp),
            )
            if (status == ModelStatus.Unsupported) {
                Text(s.modelUnsupported, style = MaterialTheme.typography.bodyMedium, color = D.red)
                return@Column
            }
            val statusText = when (status) {
                ModelStatus.Ready -> s.modelReady
                ModelStatus.Missing -> s.modelMissing
                ModelStatus.Verifying -> s.modelVerifying
                is ModelStatus.Downloading -> s.modelDownloading(percent(status.done, status.total))
                is ModelStatus.Failed -> "${s.modelFailed} (${status.reason})"
                ModelStatus.Unsupported -> s.modelUnsupported
            }
            val statusColor = when (status) {
                is ModelStatus.Failed -> D.red
                ModelStatus.Ready -> Color(0xFF23473F)
                else -> D.soft
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelTile(s.modelLight, gigabytes(ImportModelPreset.LIGHT.sizeBytes), state.model.preset == ImportModelPreset.LIGHT, statusText, statusColor) {
                    importer.choosePreset(ImportModelPreset.LIGHT)
                }
                ModelTile(s.modelAccurate, gigabytes(ImportModelPreset.ACCURATE.sizeBytes), state.model.preset == ImportModelPreset.ACCURATE, statusText, statusColor) {
                    importer.choosePreset(ImportModelPreset.ACCURATE)
                }
                state.model.customPath?.let { path ->
                    ModelTile("${s.modelCustom}: ${path.substringAfterLast('/').substringAfterLast('\\')}", "", true, statusText, statusColor) {}
                }
            }
            if (status is ModelStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { if (status.total > 0) status.done.toFloat() / status.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = D.teal,
                )
                ReinoButton(s.cancelDownload, onClick = { importer.cancelDownload() }, variant = ReinoButtonVariant.Secondary)
            } else if (state.model.preset != null && status != ModelStatus.Ready && status != ModelStatus.Verifying) {
                ReinoButton(s.downloadModel, onClick = { importer.downloadModel() }, trailingArrow = true)
            }
        }
        val canRemove = status == ModelStatus.Ready && state.model.preset != null
        if (importer.canPickModelFile || canRemove) {
            Column(Modifier.fillMaxWidth().importCard()) {
                if (importer.canPickModelFile) ActionRow(s.chooseModelFile, D.ink, first = true) { importer.chooseModelFile() }
                if (canRemove) {
                    ActionRow(
                        s.removeModel, D.red, first = !importer.canPickModelFile,
                        trailing = state.model.preset?.let { gigabytes(it.sizeBytes) },
                    ) { importer.removeModel() }
                }
            }
        }
    }
}

/** `1117320736` -> `1.1 GB`: sizes the user weighs before a download, not exact byte counts. */
internal fun gigabytes(bytes: Long): String {
    val tenths = (bytes + 50_000_000) / 100_000_000
    return "${tenths / 10}.${tenths % 10} GB"
}
