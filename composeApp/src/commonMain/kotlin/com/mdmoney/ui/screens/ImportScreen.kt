package com.mdmoney.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatMoney
import com.mdmoney.domain.Category
import com.mdmoney.domain.categoryTitle
import com.mdmoney.importer.ExtractionResult
import com.mdmoney.importer.ImportPlanner
import com.mdmoney.importer.Reconciliation
import com.mdmoney.importer.ReviewRow
import com.mdmoney.importer.SimpleDate
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.ImportPhase
import com.mdmoney.ui.ImportUiState
import com.mdmoney.ui.ModelStatus
import com.mdmoney.ui.StatementImportModel
import com.mdmoney.ui.UiState
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.components.ReinoCheckbox
import com.mdmoney.ui.components.ReinoField
import com.mdmoney.ui.components.chipRowScroll
import com.mdmoney.ui.theme.LocalReinoColors
import kotlinx.coroutines.delay

/**
 * Bank statement / card bill import: pick a PDF, watch it being read, review every line, write the
 * ticked ones into the account's ledgers. All decisions live in [StatementImportModel].
 */
@Composable
fun ImportScreen(model: AppModel, importer: StatementImportModel, app: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val state by importer.state.collectAsState()

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            ReinoButton("‹ ${s.home}", onClick = { model.closeImport() }, variant = ReinoButtonVariant.Ghost)
            Text(
                s.importStatement,
                style = MaterialTheme.typography.displayMedium,
                color = reino.ink,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            when (val phase = state.phase) {
                ImportPhase.Pick -> PickPhase(importer, state, app.categories)
                is ImportPhase.Reading -> ReadingPhase(importer, phase)
                is ImportPhase.Review -> ReviewPhase(importer, state, phase, app)
                is ImportPhase.Done -> {
                    Text(
                        s.importDone(phase.result.added, phase.result.skipped, phase.result.notes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = reino.ink,
                    )
                    Spacer(Modifier.height(20.dp))
                    ReinoButton(s.home, onClick = { model.closeImport() }, trailingArrow = true)
                    Spacer(Modifier.height(10.dp))
                    ReinoButton(s.importAnother, onClick = { importer.backToPick() }, variant = ReinoButtonVariant.Secondary)
                }
                is ImportPhase.Failed -> {
                    Eyebrow(phase.fileName ?: s.importStatement)
                    Text(
                        "${s.importFailed}: ${phase.reason}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = reino.brassDeep,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    ReinoButton(s.importAnother, onClick = { importer.backToPick() }, variant = ReinoButtonVariant.Secondary)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PickPhase(importer: StatementImportModel, state: ImportUiState, categories: List<Category>) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    Text(s.importIntro, style = MaterialTheme.typography.bodyMedium, color = reino.inkSoft)
    val notice = when (val status = state.modelStatus) {
        ModelStatus.Ready -> null
        ModelStatus.Unsupported -> s.modelUnsupported
        is ModelStatus.Downloading -> s.modelDownloading(percent(status.done, status.total))
        else -> s.modelMissingNotice
    }
    notice?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = reino.brassDeep, modifier = Modifier.padding(top = 12.dp))
    }
    Spacer(Modifier.height(20.dp))
    ReinoButton(s.choosePdf, onClick = { importer.pickAndRead(categories) }, trailingArrow = true)
}

@Composable
private fun ReadingPhase(importer: StatementImportModel, phase: ImportPhase.Reading) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    Eyebrow(phase.fileName)
    Text(
        if (phase.loadingModel) s.loadingModel else s.readingPdf,
        style = MaterialTheme.typography.bodyLarge,
        color = reino.ink,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    // Always moving, so a slow page never looks like a frozen screen; the pages are counted below.
    LinearProgressIndicator(Modifier.fillMaxWidth(), color = reino.brass)
    if (phase.total > 0) {
        Text(s.pageProgress(phase.done, phase.total), style = MaterialTheme.typography.labelMedium, color = reino.inkSoft, modifier = Modifier.padding(top = 6.dp))
    }
    phase.started?.let { started ->
        // Re-read once a second; the elapsed time is the proof it's still working.
        var elapsed by remember(started) { mutableStateOf(started.elapsedNow().inWholeSeconds) }
        LaunchedEffect(started) {
            while (true) {
                delay(1_000)
                elapsed = started.elapsedNow().inWholeSeconds
            }
        }
        Text(
            s.readingTime(clock(elapsed), phase.remainingSeconds(elapsed)?.let { (it + 59) / 60 }),
            style = MaterialTheme.typography.labelMedium,
            color = reino.inkSoft,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    Spacer(Modifier.height(20.dp))
    ReinoButton(s.cancel, onClick = { importer.cancel() }, variant = ReinoButtonVariant.Secondary)
}

@Composable
private fun ReviewPhase(importer: StatementImportModel, state: ImportUiState, phase: ImportPhase.Review, app: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val result = phase.result
    Eyebrow(phase.fileName)
    result.header.period?.let {
        Text(
            "${s.statementPeriod}: ${dateText(it.start)} – ${dateText(it.end)}" + (result.header.currency?.let { c -> " · $c" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = reino.inkSoft,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (!result.usedModel) {
        Text(s.simpleModeNotice, style = MaterialTheme.typography.bodySmall, color = reino.brassDeep, modifier = Modifier.padding(top = 8.dp))
        result.modelError?.let {
            Text("${s.modelFailedToRun}: $it", style = MaterialTheme.typography.bodySmall, color = reino.brassDeep, modifier = Modifier.padding(top = 4.dp))
        }
    }

    Spacer(Modifier.height(16.dp))
    Eyebrow(s.importAccount)
    Row(
        Modifier.chipRowScroll().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        app.accounts.forEach { acc ->
            FilterChip(selected = state.account == acc, onClick = { importer.setAccount(acc) }, label = { Text(acc) })
        }
    }
    if (ImportPlanner.currencyMismatch(result.header.currency, state.accountCurrency)) {
        Text(
            s.currencyMismatch(result.header.currency.orEmpty(), state.accountCurrency.code),
            style = MaterialTheme.typography.bodySmall,
            color = reino.brassDeep,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    Spacer(Modifier.height(16.dp))
    ReconciliationLines(result.reconciliation)

    if (result.transactions.isEmpty()) {
        Text(s.nothingFound, style = MaterialTheme.typography.bodyLarge, color = reino.inkSoft, modifier = Modifier.padding(vertical = 20.dp))
        ReinoButton(s.importAnother, onClick = { importer.backToPick() }, variant = ReinoButtonVariant.Secondary)
        return
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ReinoButton(s.selectAll, onClick = { importer.selectAll(true) }, variant = ReinoButtonVariant.Ghost)
        ReinoButton(s.selectNone, onClick = { importer.selectAll(false) }, variant = ReinoButtonVariant.Ghost)
    }
    HairlineDivider()
    state.rows.forEach { row ->
        ReviewLine(row, app.categories, importer)
        HairlineDivider()
    }

    val count = state.rows.count { it.selected }
    Spacer(Modifier.height(20.dp))
    ReinoButton(
        s.importSelected(count),
        onClick = { importer.import(app.categories, s.importedExpenseTitle, s.importedIncomeTitle) },
        enabled = count > 0 && !state.importing && state.account != null,
        trailingArrow = true,
    )
    Spacer(Modifier.height(10.dp))
    ReinoButton(s.importAnother, onClick = { importer.backToPick() }, variant = ReinoButtonVariant.Secondary)
}

/** The extracted totals next to what the statement prints, so a missed or invented line shows. */
@Composable
private fun ReconciliationLines(r: Reconciliation) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val reino = LocalReinoColors.current
    @Composable
    fun line(label: String, extracted: Double, declared: Double?, match: Boolean?) {
        val tail = declared?.let { d ->
            " · ${s.declaredTotal} ${formatMoney(d, sep)} (${if (match == true) s.totalsMatch else s.totalsDiffer})"
        }.orEmpty()
        Text(
            "$label: ${formatMoney(extracted, sep)}$tail",
            style = MaterialTheme.typography.bodyMedium,
            color = if (match == false) reino.brassDeep else reino.ink,
        )
    }
    line(s.extractedDebits, r.debits, r.declaredDebits, r.debitsMatch)
    line(s.extractedCredits, r.credits, r.declaredCredits, r.creditsMatch)
}

@Composable
private fun ReviewLine(row: ReviewRow, categories: List<Category>, importer: StatementImportModel) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val reino = LocalReinoColors.current
    val muted = row.alreadyImported
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        ReinoCheckbox(
            checked = row.selected,
            onToggle = if (muted) null else ({ importer.toggle(row.id) }),
            accent = if (row.isIncome) reino.verdigris else reino.brass,
        )
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 8.dp)) {
            ReinoField(
                value = row.description,
                onValueChange = { importer.setDescription(row.id, it) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (muted) reino.inkFaint else reino.ink),
            )
            val flags = listOfNotNull(
                dateText(row.tx.date),
                s.kindName(row.tx.kind),
                s.alreadyImported.takeIf { row.alreadyImported },
                row.tx.doubt?.let { s.doubtText(it) },
            )
            Text(
                flags.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (!row.tx.verified) reino.brassDeep else reino.inkFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
            CategoryPicker(row.category, categories) { importer.setCategory(row.id, it) }
        }
        Text(
            (if (row.isIncome) "+" else "") + formatMoney(row.tx.amount, sep),
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                muted -> reino.inkFaint
                row.isIncome -> reino.verdigris
                else -> reino.ink
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(110.dp).padding(top = 8.dp),
        )
    }
}

@Composable
private fun CategoryPicker(selected: String?, categories: List<Category>, onPick: (String?) -> Unit) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "${s.categoryLabel}: ${categoryTitle(selected, categories) ?: s.uncategorized} ▾",
            style = MaterialTheme.typography.labelMedium,
            color = reino.brass,
            modifier = Modifier.clickable { open = true }.padding(vertical = 6.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(s.uncategorized) }, onClick = { onPick(null); open = false })
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.title) }, onClick = { onPick(c.slug); open = false })
            }
        }
    }
}

private fun dateText(d: SimpleDate): String =
    "${d.day.toString().padStart(2, '0')}/${d.month.toString().padStart(2, '0')}/${d.year}"

internal fun percent(done: Long, total: Long): Int =
    if (total <= 0) 0 else ((done * 100) / total).toInt().coerceIn(0, 100)

/** `135` -> `2:15`. */
internal fun clock(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
