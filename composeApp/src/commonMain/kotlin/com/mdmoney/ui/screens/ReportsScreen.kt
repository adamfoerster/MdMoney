package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatMoney
import com.mdmoney.domain.Expense
import com.mdmoney.domain.Month
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.CategoryReport
import com.mdmoney.ui.UiState
import com.mdmoney.ui.categoryReport
import com.mdmoney.ui.categoryReports
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.components.SectionBand
import com.mdmoney.ui.theme.LocalReinoColors
import kotlin.math.roundToInt

/**
 * Where the money went: every category of the year, heaviest first, and one category's own page
 * behind each — its months as a chart, and the notes making them up.
 */
@Composable
fun ReportsScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    Surface(Modifier.fillMaxSize()) {
        val open = state.openCategory
        // A slug that no longer has spending in the year in view falls back to the list, rather
        // than showing an empty page for a category the year knows nothing about.
        val report = open?.let { categoryReport(it, state.expenses, state.year, state.categories, s.uncategorized) }
        if (report == null) CategoryList(model, state) else CategoryDetail(model, state, report)
    }
}

@Composable
private fun CategoryList(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val reports = categoryReports(state.expenses, state.year, state.categories, s.uncategorized)
    val total = reports.sumOf { it.total }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp))
        Eyebrow("${state.selectedAccount ?: s.appName} · ${state.year}")
        Text(
            s.categories,
            style = MaterialTheme.typography.displayMedium,
            color = reino.ink,
            modifier = Modifier.padding(top = 10.dp),
        )

        if (reports.isEmpty()) {
            Text(
                s.nothingSpentThisYear,
                style = MaterialTheme.typography.bodyLarge,
                color = reino.inkSoft,
                modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }

        Spacer(Modifier.height(20.dp))
        SectionBand(title = s.expenseSection, number = formatMoney(total, sep, cur), label = s.year)
        Spacer(Modifier.height(4.dp))

        // Counting is part of the brand, and the ordinal reads as a rank: 01 is where the money goes.
        reports.forEachIndexed { index, report ->
            CategoryRow(
                ordinal = (index + 1).toString().padStart(2, '0'),
                report = report,
                shareOfTotal = if (total > 0) report.total / total else 0.0,
                onClick = { model.openCategory(report.slug) },
            )
        }
        HairlineDivider()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CategoryRow(ordinal: String, report: CategoryReport, shareOfTotal: Double, onClick: () -> Unit) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        HairlineDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.Top) {
            Text(
                ordinal,
                style = MaterialTheme.typography.labelLarge,
                color = reino.brass,
                modifier = Modifier.width(30.dp).padding(top = 3.dp),
            )
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(report.category.title, style = MaterialTheme.typography.titleLarge, color = reino.ink)
                Text(
                    "${(shareOfTotal * 100).roundToInt()}% ${s.shareOfYear} · ${report.rows.size} ${s.entries}"
                        .uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = reino.inkFaint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                formatMoney(report.total, sep, cur),
                style = MaterialTheme.typography.titleMedium,
                color = reino.ink,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "→",
                style = MaterialTheme.typography.labelLarge,
                color = reino.brass,
                modifier = Modifier.padding(start = 12.dp, top = 3.dp),
            )
        }
    }
}

@Composable
private fun CategoryDetail(model: AppModel, state: UiState, report: CategoryReport) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp))
        ReinoButton(s.allCategories, onClick = { model.closeCategory() }, variant = ReinoButtonVariant.Ghost)

        Eyebrow("${s.categories} · ${state.year}", modifier = Modifier.padding(top = 10.dp))
        Text(
            report.category.title,
            style = MaterialTheme.typography.displayMedium,
            color = reino.ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        // The description is the whole point of a category having a note of its own.
        report.category.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = reino.inkSoft,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Eyebrow(s.spendByMonth, color = reino.inkFaint)
        Spacer(Modifier.height(14.dp))
        MonthBars(report)

        Spacer(Modifier.height(28.dp))
        SectionBand(title = s.total, number = formatMoney(report.total, sep, cur))
        Spacer(Modifier.height(4.dp))

        report.rows.forEach { row -> CategoryLineRow(model, row) }
        HairlineDivider()
        Spacer(Modifier.height(32.dp))
    }
}

/** One note under a category: what it is, and what it cost across the year. */
@Composable
private fun CategoryLineRow(model: AppModel, row: Expense) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    Column(
        Modifier.fillMaxWidth()
            // A ledger's amount is the sum of its purchases, so the row opens the book behind it.
            .then(if (row.ledger) Modifier.clickable { model.openLedger(row) } else Modifier),
    ) {
        HairlineDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, color = reino.ink)
                Text(
                    s.typeName(row.type).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = reino.inkFaint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                formatMoney(row.total, sep, cur),
                style = MaterialTheme.typography.titleMedium,
                color = reino.ink,
                textAlign = TextAlign.End,
            )
            if (row.ledger) {
                Text(
                    "→",
                    style = MaterialTheme.typography.labelLarge,
                    color = reino.brass,
                    modifier = Modifier.padding(start = 12.dp, top = 3.dp),
                )
            }
        }
    }
}

private val ChartHeight = 132.dp

/**
 * The year as twelve brass columns standing on a hairline — no axes, no grid, no legend.
 *
 * Everything is read against the peak printed above it, which is the one number a reader needs to
 * size the rest by; the brand draws with rules and proportion rather than chrome.
 */
@Composable
private fun MonthBars(report: CategoryReport) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val peak = report.peak

    Column(Modifier.fillMaxWidth()) {
        Text(
            formatMoney(peak, sep, cur),
            style = MaterialTheme.typography.labelSmall,
            color = reino.inkFaint,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().height(ChartHeight),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Month.ALL.forEach { month ->
                val value = report.monthly[month] ?: 0.0
                // Against the peak, so a quiet month still reads as a mark rather than nothing.
                val fraction = if (peak > 0) (value / peak).toFloat() else 0f
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(if (value > 0) 0.02f else 0f))
                            .background(if (value > 0) reino.brass else reino.brassTint),
                    )
                }
            }
        }
        HairlineDivider(strong = true)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Month.ALL.forEach { month ->
                Text(
                    s.monthShort(month).take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = reino.inkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
