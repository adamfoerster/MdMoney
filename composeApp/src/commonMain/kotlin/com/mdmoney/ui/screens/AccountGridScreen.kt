package com.mdmoney.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalStrings
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.data.formatMoney
import com.mdmoney.domain.Expense
import com.mdmoney.domain.Month
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.UiState
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.components.SectionBand
import com.mdmoney.ui.theme.LocalReinoColors
import com.mdmoney.ui.theme.LocalReinoType

private val TitleWidth = 150.dp
private val MonthWidth = 78.dp
private val TotalWidth = 92.dp
private val RowHeight = 48.dp

@Composable
fun AccountGridScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val rows = state.expenses.filter { it.year == state.year }.sortedBy { it.title.lowercase() }
    val hScroll = rememberScrollState()
    var cell by remember { mutableStateOf<Pair<Expense, Month>?>(null) }

    // Mono figures use tighter tracking than mono labels.
    val numberStyle = TextStyle(fontFamily = LocalReinoType.current.mono, fontSize = 12.sp, letterSpacing = 0.02.em)

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReinoButton("‹ ${s.accounts}", onClick = { model.back() }, variant = ReinoButtonVariant.Ghost)
                Spacer(Modifier.weight(1f))
                ReinoButton(s.addExpense, onClick = { model.openAdd() }, variant = ReinoButtonVariant.Ghost, trailingArrow = true)
            }

            SectionBand(
                title = state.selectedAccount ?: s.accounts,
                number = state.year.toString(),
                label = s.year,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (state.availableYears.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    state.availableYears.forEach { year ->
                        val selected = year == state.year
                        Text(
                            year.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) reino.brass else reino.inkFaint,
                            modifier = Modifier.clickable { model.setYear(year) },
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }

            when {
                state.loading && rows.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = reino.brass)
                    }
                rows.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                        Text(s.noExpenses, style = MaterialTheme.typography.bodyLarge, color = reino.inkSoft, textAlign = TextAlign.Center)
                    }
                else -> {
                    val incomes = rows.filter { it.isIncome }
                    val expenses = rows.filter { !it.isIncome }
                    // A ledger's amount is the sum of its purchases, so both the title and the cell
                    // open the ledger; there is no single value to edit here.
                    val open: (Expense) -> Unit = { if (it.ledger) model.openLedger(it) else model.openEdit(it) }
                    val openCell: (Expense, Month) -> Unit = { e, m ->
                        if (e.ledger) model.openLedger(e) else cell = e to m
                    }
                    HeaderRow(hScroll)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        // Income and expenses are totalled apart, then reconciled by a net row —
                        // summing them together would make the figure meaningless.
                        if (incomes.isNotEmpty()) {
                            GroupLabel(s.incomeSection)
                            incomes.forEach { expense ->
                                ExpenseRow(expense, hScroll, numberStyle, onTitle = { open(expense) }, onCell = { openCell(expense, it) })
                            }
                            TotalsRow(incomes, hScroll, numberStyle, s.total, reino.verdigris)
                            Spacer(Modifier.height(20.dp))
                        }
                        if (expenses.isNotEmpty()) {
                            if (incomes.isNotEmpty()) GroupLabel(s.expenseSection)
                            expenses.forEach { expense ->
                                ExpenseRow(expense, hScroll, numberStyle, onTitle = { open(expense) }, onCell = { openCell(expense, it) })
                            }
                            TotalsRow(expenses, hScroll, numberStyle, s.total, reino.brass)
                        }
                        if (incomes.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            NetRow(incomes, expenses, hScroll, numberStyle)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    cell?.let { (expense, month) ->
        CellEditDialog(
            expense = expense,
            month = month,
            onDismiss = { cell = null },
            onSave = { amount, paid -> model.updateCell(expense, month, amount, paid); cell = null },
        )
    }
}

@Composable
private fun HeaderRow(hScroll: ScrollState) {
    val reino = LocalReinoColors.current
    val s = LocalStrings.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        HeaderCell("", TitleWidth, TextAlign.Start)
        Row(Modifier.horizontalScroll(hScroll)) {
            Month.ALL.forEach { HeaderCell(s.monthShort(it), MonthWidth, TextAlign.End) }
            HeaderCell(s.total, TotalWidth, TextAlign.End)
        }
    }
    HairlineDivider(Modifier.padding(horizontal = 16.dp), strong = true)
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign) {
    val reino = LocalReinoColors.current
    Box(Modifier.width(width).height(34.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = reino.inkFaint,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    hScroll: ScrollState,
    numberStyle: TextStyle,
    onTitle: () -> Unit,
    onCell: (Month) -> Unit,
) {
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    Row(Modifier.fillMaxWidth().height(RowHeight)) {
        Box(
            Modifier.width(TitleWidth).height(RowHeight).clickable(onClick = onTitle).padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                expense.title,
                style = MaterialTheme.typography.bodyMedium,
                color = reino.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            Month.ALL.forEach { month ->
                MoneyCell(expense.amount(month), expense.isPaid(month), expense.isIncome, numberStyle) { onCell(month) }
            }
            Box(Modifier.width(TotalWidth).height(RowHeight).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Text(formatMoney(expense.total, sep, cur), style = numberStyle.copy(fontWeight = FontWeight.SemiBold), color = reino.ink, maxLines = 1, softWrap = false)
            }
        }
    }
    HairlineDivider()
}

@Composable
private fun MoneyCell(amount: Double?, paid: Boolean, isIncome: Boolean, numberStyle: TextStyle, onClick: () -> Unit) {
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val hasValue = amount != null && amount != 0.0
    // A settled cell is tinted: brass for a paid bill, verdigris for income received.
    val accent = if (isIncome) reino.verdigris else reino.brass
    val bg = if (paid && hasValue) (if (isIncome) reino.verdigrisTint else reino.brassTint) else reino.paper
    val color = when {
        paid && hasValue -> accent
        hasValue -> reino.ink
        else -> reino.inkFaint // empty or zero
    }
    Box(
        Modifier.width(MonthWidth).height(RowHeight).background(bg).clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(if (hasValue) formatMoney(amount!!, sep, cur) else "·", style = numberStyle, color = color, maxLines = 1, softWrap = false)
    }
}

/** Section heading inside the grid (income vs expenses). */
@Composable
private fun GroupLabel(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
        Eyebrow(text)
    }
}

/** Net per month: income minus expenses — what the year actually adds up to. */
@Composable
private fun NetRow(incomes: List<Expense>, expenses: List<Expense>, hScroll: ScrollState, numberStyle: TextStyle) {
    val reino = LocalReinoColors.current
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    fun netOf(month: Month) =
        incomes.sumOf { it.amount(month) ?: 0.0 } - expenses.sumOf { it.amount(month) ?: 0.0 }

    HairlineDivider(strong = true)
    Row(Modifier.fillMaxWidth().height(RowHeight)) {
        Box(Modifier.width(TitleWidth).height(RowHeight).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            Text(s.net.uppercase(), style = MaterialTheme.typography.labelLarge, color = reino.ink)
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            Month.ALL.forEach { month ->
                val net = netOf(month)
                Box(Modifier.width(MonthWidth).height(RowHeight).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        if (net == 0.0) "" else formatMoney(net, sep, cur),
                        style = numberStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = if (net < 0.0) reino.brassDeep else reino.verdigris,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            val grand = incomes.sumOf { it.total } - expenses.sumOf { it.total }
            Box(Modifier.width(TotalWidth).height(RowHeight).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    formatMoney(grand, sep, cur),
                    style = numberStyle.copy(fontWeight = FontWeight.Bold),
                    color = if (grand < 0.0) reino.brassDeep else reino.verdigris,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun TotalsRow(
    rows: List<Expense>,
    hScroll: ScrollState,
    numberStyle: TextStyle,
    label: String,
    accent: Color,
) {
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    HairlineDivider(strong = true)
    Row(Modifier.fillMaxWidth().height(RowHeight)) {
        Box(Modifier.width(TitleWidth).height(RowHeight).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = reino.ink)
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            Month.ALL.forEach { month ->
                val sum = rows.sumOf { it.amount(month) ?: 0.0 }
                Box(Modifier.width(MonthWidth).height(RowHeight).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(if (sum == 0.0) "" else formatMoney(sum, sep, cur), style = numberStyle.copy(fontWeight = FontWeight.SemiBold), color = reino.ink, maxLines = 1, softWrap = false)
                }
            }
            val grand = rows.sumOf { it.total }
            Box(Modifier.width(TotalWidth).height(RowHeight).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Text(formatMoney(grand, sep, cur), style = numberStyle.copy(fontWeight = FontWeight.Bold), color = accent, maxLines = 1, softWrap = false)
            }
        }
    }
}
