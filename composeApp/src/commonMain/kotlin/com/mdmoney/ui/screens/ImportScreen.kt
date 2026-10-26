package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatMoney
import com.mdmoney.domain.Category
import com.mdmoney.domain.categoryTitle
import com.mdmoney.importer.ExtractionResult
import com.mdmoney.importer.GroupChoice
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
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.LocalReinoColors
import com.mdmoney.ui.theme.box
import com.mdmoney.ui.theme.importCard
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
    // Held above the review screen, so coming back from it lands where the list was left.
    val scroll = rememberScrollState()

    (state.phase as? ImportPhase.Review)?.takeIf { it.reviewing != null }?.let {
        AmountReviewScreen(importer, state, it)
        return
    }

    Surface(Modifier.fillMaxSize(), color = D.bg) {
      Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp)) {
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
            Spacer(Modifier.height(24.dp))
        }
        (state.phase as? ImportPhase.Review)?.takeIf { state.rows.isNotEmpty() }?.let {
            ImportActionBar(importer, state, app)
        }
      }
    }
}

/** Always-visible footer of the review: the button that writes the ticked lines. */
@Composable
private fun ImportActionBar(importer: StatementImportModel, state: ImportUiState, app: UiState) {
    val s = LocalStrings.current
    val count = state.rows.count { it.selected }
    val enabled = count > 0 && !state.importing && state.account != null
    Column(
        Modifier.fillMaxWidth().background(D.card).border(1.dp, D.cardBorder)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).background(D.teal)
                .alpha(if (enabled) 1f else 0.45f)
                .clickable(enabled = enabled) {
                    importer.import(app.categories, s.importedExpenseTitle, s.importedIncomeTitle)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(s.importSelected(count), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = D.card)
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
    Text(
        phase.fileName,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MaterialTheme.typography.labelMedium.fontFamily, fontSize = 12.sp),
        color = D.soft,
        maxLines = 1,
    )
    result.header.period?.let {
        Text(
            "${s.statementPeriod}: ${dateText(it.start)} – ${dateText(it.end)}" + (result.header.currency?.let { c -> " · $c" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = D.soft,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    Spacer(Modifier.height(20.dp))
    Text(s.importAccount.uppercase(), style = MaterialTheme.typography.labelMedium, color = D.soft)
    Row(
        Modifier.chipRowScroll().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        app.accounts.forEach { acc ->
            val on = state.account == acc
            Box(
                Modifier.height(48.dp)
                    .box(if (on) D.accountBg else D.field, if (on) D.accountBorder else D.fieldBorder, 8)
                    .clickable { importer.setAccount(acc) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(acc, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium, color = D.ink)
            }
        }
    }
    if (ImportPlanner.currencyMismatch(result.header.currency, state.accountCurrency)) {
        Text(
            s.currencyMismatch(result.header.currency.orEmpty(), state.accountCurrency.code),
            style = MaterialTheme.typography.bodySmall,
            color = D.todoInk,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (!result.usedModel) {
        Column(Modifier.padding(top = 20.dp).fillMaxWidth().box(D.noteBg, D.noteBorder, 10).padding(12.dp)) {
            Text(s.simpleModeNotice, style = MaterialTheme.typography.bodySmall, color = D.noteInk)
            result.modelError?.let {
                Text("${s.modelFailedToRun}: $it", style = MaterialTheme.typography.bodySmall, color = D.noteInk, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Column(Modifier.fillMaxWidth().importCard()) {
        ReconciliationColumns(result.reconciliation)
        // Always on offer — matching totals don't prove every line is right (two errors can cancel out,
        // and a statement may print no total at all); the hint only appears when they disagree.
        if (state.rows.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
            Row(
                Modifier.fillMaxWidth().height(48.dp).clickable { importer.openAmountReview() }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(s.reviewAmounts, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = D.ink)
                Text("›", style = MaterialTheme.typography.bodyLarge, color = D.ink)
            }
        }
    }
    if (state.rows.isNotEmpty() && ImportPlanner.needsAmountReview(result.reconciliation)) {
        Text(s.reviewAmountsHint, style = MaterialTheme.typography.bodySmall, color = D.todoInk, modifier = Modifier.padding(top = 8.dp))
    }

    if (result.transactions.isEmpty()) {
        Text(s.nothingFound, style = MaterialTheme.typography.bodyLarge, color = reino.inkSoft, modifier = Modifier.padding(vertical = 20.dp))
        ReinoButton(s.importAnother, onClick = { importer.backToPick() }, variant = ReinoButtonVariant.Secondary)
        return
    }

    Spacer(Modifier.height(20.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.bar).padding(horizontal = 14.dp).height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.selectAll, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = D.ink, modifier = Modifier.clickable { importer.selectAll(true) })
        Text(s.selectNone, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = D.ink, modifier = Modifier.clickable { importer.selectAll(false) })
    }
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.rows.forEach { row ->
            ReviewLine(row, app.categories, state.groups.forRow(row), importer)
        }
    }

    Spacer(Modifier.height(20.dp))
    ReinoButton(s.importAnother, onClick = { importer.backToPick() }, variant = ReinoButtonVariant.Secondary)
}

/** In / Out side by side, each with what the statement prints under it when it printed one. */
@Composable
private fun ReconciliationColumns(r: Reconciliation) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    @Composable
    fun RowScope.cell(label: String, shown: String, tint: Color, declared: Double?, match: Boolean?) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = D.soft)
            Text(
                shown,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = if (match == false) D.red else tint,
            )
            declared?.let { d ->
                Text(
                    "${s.declaredTotal} ${formatMoney(d, sep)} (${if (match == true) s.totalsMatch else s.totalsDiffer})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (match == false) D.red else D.soft,
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        cell(s.extractedCredits, "+" + formatMoney(r.credits, sep), D.teal, r.declaredCredits, r.creditsMatch)
        Box(Modifier.width(1.dp).fillMaxHeight().background(D.rule))
        cell(s.extractedDebits, "−" + formatMoney(r.debits, sep), D.ink, r.declaredDebits, r.debitsMatch)
    }
}

/** The extracted totals next to what the statement prints, so a missed or invented line shows. */
@Composable
internal fun ReconciliationLines(r: Reconciliation) {
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
private fun ReviewLine(row: ReviewRow, categories: List<Category>, groups: List<GroupChoice>, importer: StatementImportModel) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val reino = LocalReinoColors.current
    val muted = !row.selectable
    Column(Modifier.fillMaxWidth().importCard().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReinoCheckbox(
                checked = row.selected,
                onToggle = if (muted) null else ({ importer.toggle(row.id) }),
                accent = if (row.isIncome) D.teal else D.ink,
                modifier = Modifier.offset(x = (-10).dp, y = (-10).dp).padding(end = 0.dp),
            )
            Column(Modifier.weight(1f).offset(x = (-10).dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    ReinoField(
                        value = row.description,
                        onValueChange = { importer.setDescription(row.id, it) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (muted) reino.inkFaint else D.ink,
                        ),
                    )
                    Text(
                        (if (row.isIncome) "+" else "−") + formatMoney(row.tx.amount, sep),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Medium,
                        color = when {
                            muted -> reino.inkFaint
                            row.isIncome -> D.teal
                            else -> D.ink
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val flags = listOfNotNull(
                    dateText(row.tx.date),
                    s.alreadyImported.takeIf { row.alreadyImported },
                    row.tx.doubt?.let { s.doubtText(it) },
                )
                Row(
                    Modifier.padding(start = 4.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        flags.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (!row.tx.verified) D.todoInk else D.soft,
                    )
                    Text(
                        s.kindName(row.tx.kind),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Medium,
                        color = if (row.isIncome) D.tagIncomeInk else D.tagInk,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (row.isIncome) D.tagIncome else D.tag)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryPicker(row.category, categories, Modifier.weight(1f)) { importer.setCategory(row.id, it) }
            GroupPicker(row.group, groups, Modifier.weight(1f)) { importer.setGroup(row.id, it) }
        }
    }
}

/** A bordered, dropdown-style field; [todo] paints it amber to flag a value still to be chosen. */
@Composable
private fun PickerBox(label: String, todo: Boolean, modifier: Modifier, menu: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(44.dp)
                .box(if (todo) D.todo else D.field, if (todo) D.todoBorder else D.fieldBorder, 8)
                .clickable { open = true }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = D.ink, maxLines = 1, modifier = Modifier.weight(1f))
            Text("▾", style = MaterialTheme.typography.bodySmall, color = D.soft)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
    }
}

@Composable
private fun CategoryPicker(selected: String?, categories: List<Category>, modifier: Modifier, onPick: (String?) -> Unit) {
    val s = LocalStrings.current
    PickerBox(categoryTitle(selected, categories) ?: s.uncategorized, todo = selected == null, modifier = modifier) { close ->
        DropdownMenuItem(text = { Text(s.uncategorized) }, onClick = { onPick(null); close() })
        categories.forEach { c ->
            DropdownMenuItem(text = { Text(c.title) }, onClick = { onPick(c.slug); close() })
        }
    }
}

/** Files the line under a group the account already has, instead of the one its category implies. */
@Composable
private fun GroupPicker(selected: GroupChoice?, groups: List<GroupChoice>, modifier: Modifier, onPick: (GroupChoice?) -> Unit) {
    val s = LocalStrings.current
    PickerBox(
        selected?.let { if (it.standalone) s.groupNone else it.title } ?: s.groupFollowsCategory,
        todo = false,
        modifier = modifier,
    ) { close ->
        DropdownMenuItem(text = { Text(s.groupFollowsCategory) }, onClick = { onPick(null); close() })
        DropdownMenuItem(text = { Text(s.groupNone) }, onClick = { onPick(GroupChoice.NONE); close() })
        groups.forEach { g ->
            DropdownMenuItem(text = { Text(g.title) }, onClick = { onPick(g); close() })
        }
    }
}

internal fun dateText(d: SimpleDate): String =
    "${d.day.toString().padStart(2, '0')}/${d.month.toString().padStart(2, '0')}/${d.year}"

internal fun percent(done: Long, total: Long): Int =
    if (total <= 0) 0 else ((done * 100) / total).toInt().coerceIn(0, 100)

/** `135` -> `2:15`. */
internal fun clock(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
