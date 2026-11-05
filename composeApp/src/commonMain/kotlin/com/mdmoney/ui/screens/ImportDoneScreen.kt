package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.ImportRecord
import com.mdmoney.data.formatMoney
import com.mdmoney.domain.Month
import com.mdmoney.importer.ImportDestination
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.ImportPhase
import com.mdmoney.ui.StatementImportModel
import com.mdmoney.ui.components.Icons24
import com.mdmoney.ui.components.LineIcon
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.i18n.Strings
import com.mdmoney.ui.importPeriodLabel
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.LocalReinoType
import com.mdmoney.ui.theme.importCard

/**
 * The closing screen of an import: what came in (file, period, money in and out), where each line
 * went, and the next move — open the account, import another statement, or leave.
 */
@Composable
fun ImportDoneScreen(model: AppModel, importer: StatementImportModel, done: ImportPhase.Done) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val summary = done.summary
    Surface(Modifier.fillMaxSize(), color = D.bg) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable { model.closeImport() },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons24.Close, D.ink, size = 20.dp, strokeWidth = 2f) }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                // --- Success ---
                Column(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(999.dp)).background(D.tagIncome), contentAlignment = Alignment.Center) {
                        LineIcon(Icons24.Check, D.tagIncomeInk, size = 30.dp, strokeWidth = 2.2f)
                    }
                    Text(
                        s.importedTitle,
                        style = TextStyle(fontFamily = LocalReinoType.current.serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 35.sp),
                        color = D.ink,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        boldMarked(s.importedSummary(summary.lines, summary.destinations.size, done.account)),
                        fontSize = 15.sp, lineHeight = 22.sp, color = D.soft, textAlign = TextAlign.Center,
                    )
                    if (done.result.skipped > 0) {
                        Text(s.skippedNotice(done.result.skipped), fontSize = 13.sp, color = D.noteInk, textAlign = TextAlign.Center)
                    }
                }

                // --- Source ---
                done.fileName?.let { name ->
                    Row(
                        Modifier.fillMaxWidth().importCard().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LineIcon(Icons24.File, D.soft, size = 20.dp, strokeWidth = 1.6f)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MaterialTheme.typography.labelMedium.fontFamily, fontSize = 12.sp),
                                color = D.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val short = { m: Int -> s.monthShort(Month.ALL[m - 1]) }
                            val period = done.period?.let {
                                importPeriodLabel(
                                    ImportRecord.iso(it.start.year, it.start.month, it.start.day),
                                    ImportRecord.iso(it.end.year, it.end.month, it.end.day),
                                    short,
                                )
                            }
                            listOfNotNull(period, done.currency).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                                Text(it, fontSize = 12.sp, color = D.soft)
                            }
                        }
                    }
                }

                // --- Totals ---
                Row(Modifier.fillMaxWidth().importCard().height(IntrinsicSize.Min)) {
                    DoneTotal(s.extractedCredits, "+" + formatMoney(summary.credits, sep), D.teal, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(D.rule))
                    DoneTotal(s.extractedDebits, "−" + formatMoney(summary.debits, sep), D.ink, Modifier.weight(1f))
                }

                // --- Where they went ---
                if (summary.destinations.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(s.whereTheyWent)
                        Column(Modifier.fillMaxWidth().importCard()) {
                            summary.destinations.forEachIndexed { i, d ->
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                                DestinationRow(d, s, sep) { model.openAccount(done.account) }
                            }
                        }
                    }
                }
            }

            // --- Actions ---
            Column(
                Modifier.fillMaxWidth().background(D.card).border(1.dp, D.cardBorder)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReinoButton(s.viewAccount(done.account), onClick = { model.openAccount(done.account) }, modifier = Modifier.fillMaxWidth(), trailingArrow = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReinoButton(s.importAnother, onClick = { importer.backToPick() }, modifier = Modifier.weight(1f), variant = ReinoButtonVariant.Secondary)
                    ReinoButton(s.home, onClick = { model.closeImport() }, modifier = Modifier.weight(1f), variant = ReinoButtonVariant.Secondary)
                }
            }
        }
    }
}

@Composable
private fun DoneTotal(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = D.soft)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

@Composable
private fun DestinationRow(d: ImportDestination, s: Strings, sep: Char, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = D.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(s.typeName(d.type), monthsLabel(d.months, s), s.linesCount(d.lines)).joinToString(" · "),
                fontSize = 12.sp, color = D.soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            (if (d.income) "+" else "") + formatMoney(d.total, sep),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"),
            color = if (d.income) D.teal else D.ink,
            maxLines = 1,
        )
        LineIcon(Icons24.ChevronRight, Color(0xFF8A847A), size = 16.dp, strokeWidth = 2f)
    }
}

/** `julho`, `julho, agosto`, or `jul – set` once there are more than two. */
internal fun monthsLabel(months: List<Month>, s: Strings): String = when {
    months.size <= 2 -> months.joinToString(", ") { s.month(it).lowercase() }
    else -> "${s.monthShort(months.first()).lowercase()} – ${s.monthShort(months.last()).lowercase()}"
}
