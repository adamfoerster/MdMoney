package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatInput
import com.mdmoney.data.formatMoney
import com.mdmoney.data.parseAmount
import com.mdmoney.domain.Category
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import com.mdmoney.domain.categoryTitle
import com.mdmoney.domain.resolveCategorySlug
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.EditorState
import com.mdmoney.ui.filterCategories
import com.mdmoney.ui.fixedAmounts
import com.mdmoney.ui.repeatMonths
import com.mdmoney.ui.yearTotal
import com.mdmoney.ui.components.Icons24
import com.mdmoney.ui.components.LineIcon
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.LocalReinoType

private val Plum = Color(0xFF463A6B)
private val Field = Color(0xFFC9C0AF)

/**
 * New / edit expense as a bottom sheet with two pages: the form, and the category picker it opens.
 * What the form asks for follows the type — a one-off its amount and month, a fixed bill its amount
 * and the months it repeats in, a variable bill only the months' worth of reading to come.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseSheet(
    model: AppModel,
    editor: EditorState,
    categories: List<Category> = emptyList(),
    groups: List<String> = emptyList(),
    accountTitle: String = "",
    year: Int = 0,
    mostUsed: List<String> = emptyList(),
) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val initial = editor.initial
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initial.title) }
    // The note stores the slug the link points at; the picker speaks titles.
    var category by remember { mutableStateOf(initial.category) }
    var type by remember { mutableStateOf(initial.type) }
    var picking by remember { mutableStateOf(false) }
    var fixedAmount by remember {
        mutableStateOf(initial.amounts.values.firstOrNull { it != null }?.let { formatInput(it, sep) } ?: "")
    }
    var months by remember { mutableStateOf(repeatMonths(initial)) }
    val eventualMonthInit = Month.ALL.firstOrNull { initial.amount(it) != null } ?: editor.defaultMonth ?: Month.ALL[0]
    val eventualMonth = eventualMonthInit
    var eventualAmount by remember {
        mutableStateOf(initial.amount(eventualMonthInit)?.let { formatInput(it, sep) } ?: "")
    }

    ModalBottomSheet(
        onDismissRequest = { model.closeEditor() },
        sheetState = sheetState,
        containerColor = D.bg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        if (picking) {
            CategoryPickerPage(
                categories = categories,
                mostUsed = mostUsed,
                selected = category,
                onPick = { category = it; picking = false },
                onBack = { picking = false },
            )
            return@ModalBottomSheet
        }

        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$accountTitle · $year".uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = D.soft)
                        Text(
                            if (editor.isNew) (if (type == ExpenseType.INCOME) s.addIncome else s.addExpense) else s.editExpense,
                            style = TextStyle(fontFamily = LocalReinoType.current.serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 32.sp),
                            color = D.ink,
                        )
                    }
                    IconTap(Icons24.Close, s.close) { model.closeEditor() }
                }

                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Labelled(s.nameLabel) {
                        SheetInput(title, { title = it }, placeholder = "")
                    }
                    Labelled(s.categoryLabel) {
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(D.card)
                                .border(1.dp, Field, RoundedCornerShape(10.dp)).clickable { picking = true }
                                .padding(start = 14.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(Plum))
                            Text(
                                categoryTitle(category, categories) ?: "—",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                color = D.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(s.change, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = D.soft)
                            LineIcon(Icons24.ChevronRight, Color(0xFF8A847A), size = 16.dp, strokeWidth = 2f)
                        }
                    }
                    Labelled(s.typeLabel) {
                        TypeGrid(type) { type = it }
                    }

                    when (type) {
                        ExpenseType.EVENTUAL -> {
                            Labelled(s.amountLabel) { MoneyInput(eventualAmount, { eventualAmount = it }, cur) }
                            Hint(s.oneOffNewHint)
                        }
                        ExpenseType.RECURRING_FIXED -> {
                            Labelled(s.amountPerMonth) { MoneyInput(fixedAmount, { fixedAmount = it }, cur) }
                            RepeatsIn(months, { months = it }) {
                                val total = formatMoney(yearTotal(parseAmount(fixedAmount), months), sep, cur)
                                if (months.size == 12) s.everyMonthSummary(year, total)
                                else "${s.monthsSelected(months.size)} · $total"
                            }
                        }
                        ExpenseType.RECURRING_VARIABLE -> Hint(s.variableNewHint)
                        ExpenseType.INCOME -> Hint(s.incomeAmountHint)
                    }

                    // A plain one-off can be folded into its month's group; the note then goes away.
                    editor.original?.takeIf { it.groupableMonth != null }?.let { original ->
                        MoveToGroup(groups, onMove = { model.groupExpense(original, it) })
                    }
                }
            }

            Column(Modifier.fillMaxWidth().background(D.card)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(D.cardBorder))
                val ready = title.isNotBlank() && !(type == ExpenseType.RECURRING_FIXED && months.isEmpty())
                Box(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp).fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(10.dp)).background(if (ready) D.dark else D.dim)
                        .then(if (ready) Modifier.clickable {
                            model.saveExpense(
                                editor.original,
                                buildExpense(initial, title, category, type, fixedAmount, months, eventualMonth, eventualAmount),
                            )
                        } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.saveExpense, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontWeight = FontWeight.Medium, color = D.onDark)
                }
            }
        }
    }
}

@Composable
private fun IconTap(icon: List<String>, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        LineIcon(icon, D.ink, size = 20.dp, strokeWidth = 2f)
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold, color = D.ink)
        content()
    }
}

@Composable
private fun SheetInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(D.card)
            .border(1.dp, Field, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = D.ink),
            cursorBrush = SolidColor(D.ink),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = D.raw)
    }
}

@Composable
private fun MoneyInput(value: String, onChange: (String) -> Unit, symbol: String) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).background(D.card)
            .border(1.dp, Field, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (symbol.isNotEmpty()) Text(symbol, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = D.soft)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", color = D.ink),
            cursorBrush = SolidColor(D.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The four kinds as a two-by-two of radio cards; the chosen one is outlined and tinted in plum. */
@Composable
private fun TypeGrid(selected: ExpenseType, onSelect: (ExpenseType) -> Unit) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseType.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { t ->
                    val on = t == selected
                    val shape = RoundedCornerShape(10.dp)
                    Row(
                        Modifier.weight(1f).heightIn(min = 64.dp).clip(shape)
                            .background(if (on) D.accountBg else D.card)
                            .border(if (on) 2.dp else 1.dp, if (on) Plum else Color(0xFFD6CDBC), shape)
                            .clickable { onSelect(t) }.padding(if (on) 11.dp else 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.padding(top = 1.dp).size(18.dp).clip(RoundedCornerShape(999.dp))
                                .border(1.5.dp, if (on) Plum else D.raw, RoundedCornerShape(999.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(Plum))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(s.typeName(t), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.SemiBold, color = D.ink)
                            Text(s.typeHint(t), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = D.soft)
                        }
                    }
                }
            }
        }
    }
}

/** "Repeats in": whole year or a hand-picked set of months, with a one-line summary beneath. */
@Composable
private fun RepeatsIn(months: Set<Month>, onChange: (Set<Month>) -> Unit, summary: () -> String) {
    val s = LocalStrings.current
    var pick by remember { mutableStateOf(months.size != 12) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(s.repeatsIn, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold, color = D.ink)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.bar).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to s.wholeYear, true to s.pickMonths).forEach { (isPick, label) ->
                val on = pick == isPick
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (on) D.card else Color.Transparent)
                        .clickable { pick = isPick; if (!isPick) onChange(Month.ALL.toSet()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = if (on) D.ink else D.soft)
                }
            }
        }
        if (pick) MonthGrid(months) { m -> onChange(if (m in months) months - m else months + m) }
        Text(summary(), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = D.soft)
    }
}

/** Twelve month toggles, four to a row. [onToggle] gets the month that was tapped. */
@Composable
private fun MonthGrid(selected: Set<Month>, onToggle: (Month) -> Unit) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Month.ALL.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    val on = m in selected
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(shape).background(if (on) D.dark else D.card)
                            .border(1.dp, if (on) D.dark else Color(0xFFD6CDBC), shape).clickable { onToggle(m) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.monthShort(m).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                            color = if (on) D.onDark else D.soft,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.bar).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIcon(Icons24.Info, D.soft, size = 18.dp)
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = D.soft)
    }
}

private fun buildExpense(
    initial: Expense,
    title: String,
    category: String?,
    type: ExpenseType,
    fixedAmount: String,
    months: Set<Month>,
    eventualMonth: Month,
    eventualAmount: String,
): Expense {
    val amounts = initial.amounts.toMutableMap()
    when (type) {
        ExpenseType.RECURRING_FIXED -> amounts.putAll(fixedAmounts(parseAmount(fixedAmount), months))
        ExpenseType.EVENTUAL -> amounts[eventualMonth] = parseAmount(eventualAmount)
        ExpenseType.RECURRING_VARIABLE,
        ExpenseType.INCOME -> { /* amounts entered per-month in the grid / on Home */ }
    }
    return initial.copy(title = title.trim(), category = category, type = type, amounts = amounts)
}
