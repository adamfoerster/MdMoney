package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.mdmoney.ui.UiState
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.monthDetail
import com.mdmoney.ui.monthSpent
import com.mdmoney.ui.valueIn
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.LocalReinoColors
import com.mdmoney.ui.theme.importCard

/**
 * The year at a glance: a strip of months with what each one spent, and the selected month's income
 * and expenses beneath it. Editing happens where it always did — tapping a line opens its expense
 * (or its ledger).
 */
@Composable
fun AccountGridScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    // null is the whole year: the strip's last item, each line then showing its total over the year.
    var month by remember { mutableStateOf<Month?>(state.homeMonth) }
    val detail = monthDetail(state.expenses, state.year, month)
    val open: (Expense) -> Unit = { if (it.ledger) model.openLedger(it) else model.openEdit(it) }

    Surface(Modifier.fillMaxSize(), color = D.bg) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReinoButton("‹ ${s.accounts}", onClick = { model.back() }, variant = ReinoButtonVariant.Ghost)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(999.dp)).background(D.dark)
                        .clickable { model.openAdd() }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+  ${s.addExpense}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = D.onDark)
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // --- Title + year ---
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.annual.uppercase(), style = MaterialTheme.typography.labelMedium, color = D.soft)
                        Text(
                            state.selectedAccount ?: s.accounts,
                            style = MaterialTheme.typography.displayMedium,
                            color = D.ink,
                        )
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(D.field)
                            .border(1.dp, D.fieldBorder, RoundedCornerShape(999.dp)),
                    ) {
                        state.availableYears.ifEmpty { listOf(state.year) }.forEach { year ->
                            val on = year == state.year
                            Text(
                                year.toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"),
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) D.ink else D.soft,
                                modifier = Modifier.clickable { model.setYear(year) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                if (state.loading && state.expenses.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LocalReinoColors.current.brass)
                    }
                    return@Column
                }

                // --- Month strip ---
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.expenseSection.uppercase(), style = MaterialTheme.typography.labelMedium, color = D.soft, modifier = Modifier.padding(start = 2.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (Month.ALL + null).forEach { m ->
                            val spent = monthSpent(state.expenses, state.year, m)
                            val on = m == month
                            Column(
                                Modifier.width(92.dp).heightIn(min = 64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) D.dark else D.card)
                                    .border(1.dp, if (on) D.dark else D.fieldBorder, RoundedCornerShape(12.dp))
                                    .clickable { month = m }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text((m?.let { s.monthShort(it) } ?: s.total).uppercase(), style = MaterialTheme.typography.labelMedium, color = if (on) D.dim else D.soft)
                                Text(
                                    spent?.let { formatMoney(it, sep) } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
                                    fontWeight = if (spent == null) FontWeight.Normal else FontWeight.Medium,
                                    color = when {
                                        on -> D.onDark
                                        spent == null -> Color(0xFF8A847A)
                                        else -> D.ink
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // --- Selected month ---
                Column(Modifier.fillMaxWidth().importCard()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(month?.let { "${s.month(it)} ${state.year}" } ?: state.year.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = D.ink)
                            Text(s.net, style = MaterialTheme.typography.bodySmall, color = D.soft)
                        }
                        Text(
                            (if (detail.net >= 0) "+" else "−") + formatMoney(kotlin.math.abs(detail.net), sep, cur),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium,
                            color = if (detail.net < 0) D.red else D.teal,
                        )
                    }
                    if (detail.isEmpty) {
                        Text(s.nothingThisMonth, style = MaterialTheme.typography.bodyMedium, color = D.soft, modifier = Modifier.padding(16.dp))
                    }
                    if (detail.income.isNotEmpty()) {
                        MonthBand(s.incomeSection, "+" + formatMoney(detail.incomeTotal, sep, cur), D.teal)
                        detail.income.forEach { DetailLine(it, month, D.teal) { open(it) } }
                    }
                    if (detail.expenses.isNotEmpty()) {
                        MonthBand(s.expenseSection, "−" + formatMoney(detail.expenseTotal, sep, cur), D.ink)
                        detail.expenses.forEach { DetailLine(it, month, D.ink) { open(it) } }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun MonthBand(label: String, total: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().background(D.bg).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = D.soft, modifier = Modifier.weight(1f))
        Text(
            total,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DetailLine(expense: Expense, month: Month?, color: Color, onClick: () -> Unit) {
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(expense.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = D.ink, maxLines = 2, modifier = Modifier.weight(1f))
        Text(
            formatMoney(expense.valueIn(month), sep, cur),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"),
            color = color,
        )
    }
}
