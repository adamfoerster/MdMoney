package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatInput
import com.mdmoney.data.formatMoney
import com.mdmoney.data.parseAmount
import com.mdmoney.domain.Month
import com.mdmoney.ui.yearTotal
import com.mdmoney.domain.ExpenseType
import com.mdmoney.importer.GroupChoice
import com.mdmoney.ui.components.Icons24
import com.mdmoney.ui.components.LineIcon
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.LocalReinoType

private val Plum = Color(0xFF463A6B)
private val Field = Color(0xFFC9C0AF)

@Composable
private fun PickerHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            LineIcon(Icons24.ChevronLeft, D.ink, size = 22.dp, strokeWidth = 2f)
        }
        Text(
            title,
            style = TextStyle(fontFamily = LocalReinoType.current.serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
            color = D.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(44.dp))
    }
}

/**
 * The kind of entry a statement line is: money received, a one-off expense, or a recurring one
 * (fixed or variable). [auto] is the first row — follow the line's category, as before — when set.
 */
@Composable
internal fun EntryTypePickerPage(
    types: List<ExpenseType>,
    selected: ExpenseType?,
    autoLabel: String,
    onPick: (ExpenseType?) -> Unit,
    onBack: () -> Unit,
    /** When set, a last row files the line as a transfer to (or from) another account. */
    transferSelected: Boolean = false,
    onTransfer: (() -> Unit)? = null,
) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        PickerHeader(s.entryTypeTitle, onBack)
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(D.card).border(1.dp, D.cardBorder, RoundedCornerShape(12.dp)),
        ) {
            val rows: List<Pair<ExpenseType?, String>> = listOf<Pair<ExpenseType?, String>>(null to autoLabel) +
                types.map { it to s.typeName(it) }
            val all = rows.map { Triple(it.first, it.second, false) } +
                (if (onTransfer != null) listOf(Triple<ExpenseType?, String, Boolean>(null, s.transferLabel, true)) else emptyList())
            all.forEachIndexed { i, (type, label, isTransfer) ->
                val on = if (isTransfer) transferSelected else !transferSelected && type == selected
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).background(if (on) D.accountBg else Color.Transparent)
                        .clickable { if (isTransfer) onTransfer?.invoke() else onPick(type) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = D.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (on) LineIcon(Icons24.Check, Plum, size = 18.dp, strokeWidth = 2.4f)
                }
            }
        }
    }
}

/** The account on the other side of a transfer line, to pick from [accounts]. */
@Composable
internal fun TransferAccountPage(
    title: String,
    accounts: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        PickerHeader(title, onBack)
        if (accounts.isEmpty()) {
            Text(s.noOtherAccounts, style = MaterialTheme.typography.bodyMedium, color = D.soft, modifier = Modifier.padding(20.dp))
            return
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(D.card).border(1.dp, D.cardBorder, RoundedCornerShape(12.dp)),
        ) {
            accounts.forEachIndexed { i, account ->
                val on = account == selected
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).background(if (on) D.accountBg else Color.Transparent)
                        .clickable { onPick(account) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        account,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = D.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (on) LineIcon(Icons24.Check, Plum, size = 18.dp, strokeWidth = 2.4f)
                }
            }
        }
    }
}

/**
 * The items of one [type] the account has, to pick from — or to create a new one by typing its name,
 * as the category page does. [allowStandalone] adds a first row that files the line as a note of its own.
 */
@Composable
internal fun ItemPickerPage(
    type: ExpenseType,
    items: List<GroupChoice>,
    selected: GroupChoice?,
    allowStandalone: Boolean,
    /** The line's own description, amount and year: what a new item starts from. */
    suggestedName: String,
    suggestedAmount: Double?,
    year: Int,
    onPick: (GroupChoice) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    // The name being given to a new item; non-null while its form is open.
    var draft by remember { mutableStateOf<String?>(null) }
    draft?.let { start ->
        NewItemForm(type, start, suggestedAmount, year, onCreate = onPick, onBack = { draft = null })
        return
    }
    var query by remember { mutableStateOf("") }
    val name = query.trim()
    // An item created a moment ago and picked again isn't in the account yet, but is still a choice.
    val all = if (selected != null && !selected.standalone && selected !in items) listOf(selected) + items else items
    val shown = if (name.isEmpty()) all else all.filter { it.title.contains(name, ignoreCase = true) }
    val exists = all.any { it.title.equals(name, ignoreCase = true) }
    val canCreate = name.isNotEmpty() && !exists

    Column(Modifier.fillMaxWidth()) {
        PickerHeader(s.typeName(type), onBack)
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp))
                .background(D.card).border(1.dp, Field, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LineIcon(Icons24.Search, D.raw, size = 18.dp, strokeWidth = 2f)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = D.ink),
                    cursorBrush = SolidColor(D.ink),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) Text(s.searchOrCreate, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = D.raw)
            }
        }

        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { ItemHeading(s.allItems) }
                Text(shown.size.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = D.soft, modifier = Modifier.padding(top = 14.dp, end = 4.dp))
            }
            if (all.isEmpty() && name.isEmpty() && !allowStandalone) {
                Text(s.noItemsYet, style = MaterialTheme.typography.bodyMedium, color = D.soft, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D.card).border(1.dp, D.cardBorder, RoundedCornerShape(12.dp))) {
                    // A null choice is the row that opens the new-item form, with the typed name.
                    val rows = buildList<Pair<GroupChoice?, String>> {
                        if (allowStandalone && name.isEmpty()) add(GroupChoice.NONE to s.groupNone)
                        if (canCreate) add(null to s.createItem(name))
                        shown.forEach { add(it to it.title) }
                    }
                    rows.forEachIndexed { i, (choice, label) ->
                        val on = choice != null && choice == selected
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(if (on) D.accountBg else Color.Transparent)
                                .clickable { if (choice == null) draft = name else onPick(choice) }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = D.ink, modifier = Modifier.weight(1f))
                            if (on) LineIcon(Icons24.Check, Plum, size = 18.dp, strokeWidth = 2.4f)
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(D.card)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(D.cardBorder))
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp).fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(10.dp)).dashedBox(Color(0xFF8A847A), 10)
                    .clickable { draft = name.ifEmpty { suggestedName } },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                LineIcon(Icons24.Plus, D.ink, size = 18.dp, strokeWidth = 2f)
                Text(s.newItem, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontWeight = FontWeight.Medium, color = D.ink)
            }
        }
    }
}

@Composable
private fun ItemHeading(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = D.soft, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
}

/**
 * What a new item needs before it exists: its name (the line's description to start with) and, for a
 * fixed bill, its amount (the line's) and the months it repeats in. A variable bill, a one-off group
 * and an income group need only the name.
 */
@Composable
private fun NewItemForm(
    type: ExpenseType,
    startName: String,
    suggestedAmount: Double?,
    year: Int,
    onCreate: (GroupChoice) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    var name by remember { mutableStateOf(startName) }
    var amount by remember { mutableStateOf(suggestedAmount?.let { formatInput(it, sep) } ?: "") }
    var months by remember { mutableStateOf(Month.ALL.toSet()) }
    val fixed = type == ExpenseType.RECURRING_FIXED
    val ready = name.isNotBlank() && !(fixed && months.isEmpty())

    Column(Modifier.fillMaxWidth()) {
        PickerHeader(s.newItem, onBack)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(s.typeName(type).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = D.soft)
            Labelled(s.nameLabel) { SheetInput(name, { name = it }, placeholder = "") }
            when (type) {
                ExpenseType.RECURRING_FIXED -> {
                    Labelled(s.amountPerMonth) { MoneyInput(amount, { amount = it }, cur) }
                    RepeatsIn(months, { months = it }) {
                        val total = formatMoney(yearTotal(parseAmount(amount), months), sep, cur)
                        if (months.size == 12) s.everyMonthSummary(year, total) else "${s.monthsSelected(months.size)} · $total"
                    }
                }
                ExpenseType.RECURRING_VARIABLE -> Hint(s.variableNewHint)
                ExpenseType.INCOME -> Hint(s.incomeAmountHint)
                ExpenseType.EVENTUAL -> Hint(s.groupHint)
            }
        }
        Column(Modifier.fillMaxWidth().background(D.card)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(D.cardBorder))
            Box(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp).fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(10.dp)).background(if (ready) D.dark else D.dim)
                    .then(if (ready) Modifier.clickable { onCreate(GroupChoice.created(name, type, parseAmount(amount), months)) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.createAndUse, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontWeight = FontWeight.Medium, color = D.onDark)
            }
        }
    }
}
