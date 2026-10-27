package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatMoney
import com.mdmoney.domain.Category
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.MergeMode
import com.mdmoney.domain.mergedAmount
import com.mdmoney.domain.categoryTitle
import com.mdmoney.domain.needsMergeChoice
import com.mdmoney.domain.oneOffDestinations
import com.mdmoney.domain.recurringDestinations
import com.mdmoney.ui.components.Icons24
import com.mdmoney.ui.components.LineIcon
import com.mdmoney.ui.formatEntryDate
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.box

private val Border = Color(0xFFB8AE9A)

/** What a tap on a purchase offers: move it to another expense, edit it, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryActionsSheet(
    ledger: Ledger,
    entry: LedgerEntry,
    onMove: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = D.bg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(D.card)) {
                Row(
                    Modifier.fillMaxWidth().background(D.bar).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.note, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.SemiBold, color = D.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.entryIn(formatEntryDate(entry.date, s), ledger.title), style = MaterialTheme.typography.bodySmall, color = D.soft)
                    }
                    Text(formatMoney(entry.amount, sep, cur), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium, color = D.ink)
                }
                ActionRow(Icons24.MoveRight, D.accent, s.moveToAnother, s.oneOrRecurring, chevron = true, onClick = onMove)
                ActionRow(Icons24.Pencil, D.soft, s.editEntry, null, onClick = onEdit)
                ActionRow(Icons24.Trash, D.red, s.deleteEntry, null, textColor = D.red, onClick = onDelete)
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(D.card).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text(s.cancel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = D.ink) }
        }
    }
}

@Composable
private fun ActionRow(
    icon: List<String>,
    iconColor: Color,
    label: String,
    sub: String?,
    textColor: Color = D.ink,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
    Row(
        Modifier.fillMaxWidth().background(D.card).clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LineIcon(icon, iconColor)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (sub != null) FontWeight.Medium else FontWeight.Normal, color = textColor)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = D.soft) }
        }
        if (chevron) LineIcon(Icons24.ChevronRight, Color(0xFF8A847A), size = 16.dp, strokeWidth = 2f)
    }
}

/**
 * Picks where a purchase goes: another one-off expense of its month (or a new one), or a recurring
 * expense. The recurring branch hands back the choice and lets [onPickRecurring] decide whether the
 * month's existing value needs a question.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveEntrySheet(
    ledger: Ledger,
    entry: LedgerEntry,
    expenses: List<Expense>,
    categories: List<Category>,
    onBack: () -> Unit,
    onMoveToOneOff: (String) -> Unit,
    onPickRecurring: (Expense) -> Unit,
) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val month = ledger.month
    val oneOffs = remember(expenses, ledger) { oneOffDestinations(expenses, ledger) }
    val recurring = remember(expenses, ledger) { if (ledger.income) emptyList() else recurringDestinations(expenses, ledger.year) }
    var recurringTab by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pickedId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val shown = (if (recurringTab) recurring else oneOffs).filter { it.title.contains(query.trim(), ignoreCase = true) }
    val picked = shown.firstOrNull { it.id == pickedId }
    val remaining = ledger.entries.size - 1

    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = D.bg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    LineIcon(Icons24.ChevronLeft, D.ink, strokeWidth = 2f)
                }
                Text(s.moveEntryTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = D.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp))
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // The purchase being moved.
                Row(Modifier.fillMaxWidth().box(Color.Transparent, Border, 12).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.note, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium, color = D.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.entryFrom(formatEntryDate(entry.date, s), ledger.title), style = MaterialTheme.typography.bodySmall, color = D.soft)
                    }
                    Text(formatMoney(entry.amount, sep, cur), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium, color = D.ink)
                }

                if (recurring.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.bar).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Segment(s.destOneOff, !recurringTab, Modifier.weight(1f)) { recurringTab = false; pickedId = null }
                        Segment(s.destRecurring, recurringTab, Modifier.weight(1f)) { recurringTab = true; pickedId = null; creating = false }
                    }
                }

                if (!recurringTab) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(s.searchOneOffs(month)) },
                        leadingIcon = { LineIcon(Icons24.Search, D.raw, size = 16.dp, strokeWidth = 2f) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (!recurringTab) {
                    Column(Modifier.fillMaxWidth().box(if (creating) D.accountBg else D.accentSoft, D.cardBorder, 12)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { creating = true; pickedId = null }.heightIn(min = 56.dp).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).background(D.accountBg), contentAlignment = Alignment.Center) {
                                LineIcon(Icons24.Plus, D.accent, size = 16.dp, strokeWidth = 2.2f)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(s.newOneOffTitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.SemiBold, color = D.accent)
                                Text(s.newOneOffSub, style = MaterialTheme.typography.bodySmall, color = D.soft)
                            }
                            if (creating) LineIcon(Icons24.Check, D.accent, size = 18.dp, strokeWidth = 2.4f)
                        }
                        if (creating) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text(s.nameLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 12.dp),
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        (if (recurringTab) s.recurringListHead(month) else s.oneOffListHead(month, ledger.year)).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = D.soft,
                    )
                    Text(shown.size.toString(), style = MaterialTheme.typography.labelSmall, color = D.soft)
                }
                Column(Modifier.fillMaxWidth().box(D.card, D.cardBorder, 12)) {
                    shown.forEachIndexed { i, option ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                        val on = option.id == pickedId
                        val value = option.amounts[month]
                        Row(
                            Modifier.fillMaxWidth().background(if (on) D.accountBg else Color.Transparent)
                                .clickable { pickedId = option.id; creating = false }
                                .heightIn(min = 56.dp).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(option.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = D.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (recurringTab) {
                                        val tag = if (option.type == ExpenseType.RECURRING_FIXED) s.fixedTag else s.variableTag
                                        Text(tag, style = MaterialTheme.typography.labelSmall, color = D.tagInk, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(D.tag).padding(horizontal = 7.dp, vertical = 1.dp))
                                    }
                                }
                                categoryTitle(option.category, categories)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = D.soft, maxLines = 1) }
                            }
                            Text(
                                value?.takeIf { it != 0.0 || !recurringTab }?.let { formatMoney(it, sep) } ?: if (recurringTab) s.notSet else "—",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = if (on) D.ink else D.soft,
                            )
                            if (on) LineIcon(Icons24.Check, D.accent, size = 18.dp, strokeWidth = 2.4f)
                        }
                    }
                }
                if (recurringTab) Text(s.recurringMoveHint(month), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = D.soft, modifier = Modifier.padding(horizontal = 4.dp))
            }

            // --- Footer: what the move does, and the button that does it ---
            val targetName = if (creating) newName.trim() else picked?.title.orEmpty()
            val ready = targetName.isNotBlank() && (recurringTab || creating || picked != null)
            Column(Modifier.fillMaxWidth().background(D.card).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!recurringTab && ready) {
                    val sourcePart = if (remaining <= 0) s.becomesEmpty(ledger.title) else s.becomes(ledger.title, formatMoney(ledger.total - entry.amount, sep, cur))
                    val targetPart = s.becomes(targetName, formatMoney((if (creating) 0.0 else picked?.amounts?.get(month) ?: 0.0) + entry.amount, sep, cur))
                    Text("$targetPart · $sourcePart", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = D.soft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(if (ready) D.dark else D.dark.copy(alpha = 0.35f))
                        .clickable(enabled = ready) {
                            if (recurringTab) picked?.let(onPickRecurring) else onMoveToOneOff(targetName)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (recurringTab) s.continueWith(targetName.ifBlank { "…" }) else s.moveTo(targetName.ifBlank { "…" }),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = D.onDark,
                    )
                }
            }
        }
    }
}

@Composable
private fun Segment(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp)).background(if (on) D.card else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = if (on) D.ink else D.soft)
    }
}

/**
 * Asked when the recurring expense already holds a value in the month: make the purchase the value,
 * or add it onto what is there. Shows both results before either is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MergeChoiceSheet(
    ledger: Ledger,
    entry: LedgerEntry,
    target: Expense,
    onBack: () -> Unit,
    onConfirm: (MergeMode) -> Unit,
) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val month = ledger.month
    val existing = target.amounts[month]
    var mode by remember { mutableStateOf(MergeMode.REPLACE) }
    val result = mergedAmount(existing, entry.amount, mode)

    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = D.bg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.movingTo(target.title, month, ledger.year).uppercase(), style = MaterialTheme.typography.labelMedium, color = D.soft)
                    Text(
                        s.alreadyHas(target.title, formatMoney(existing ?: 0.0, sep, cur), month),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 30.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = D.ink,
                    )
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.bar).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${entry.note} · ${formatEntryDate(entry.date, s)}", style = MaterialTheme.typography.bodyMedium, color = D.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatMoney(entry.amount, sep, cur), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold, color = D.ink)
                }
                MergeOption(
                    on = mode == MergeMode.REPLACE,
                    title = s.useAsValue(month),
                    hint = s.useAsValueHint,
                    calc = "${formatMoney(existing ?: 0.0, sep)} → ",
                    strike = true,
                    result = formatMoney(entry.amount, sep, cur),
                ) { mode = MergeMode.REPLACE }
                MergeOption(
                    on = mode == MergeMode.ADD,
                    title = s.addToValue(month),
                    hint = s.addToValueHint,
                    calc = "${formatMoney(existing ?: 0.0, sep)} + ${formatMoney(entry.amount, sep)} = ",
                    strike = false,
                    result = formatMoney(mergedAmount(existing, entry.amount, MergeMode.ADD), sep, cur),
                ) { mode = MergeMode.ADD }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    LineIcon(Icons24.Info, D.soft, size = 16.dp)
                    val emptied = if (ledger.entries.size <= 1) " " + s.noEntriesLeft(ledger.title) else ""
                    Text(s.markedPaid(target.title, month) + emptied, style = MaterialTheme.typography.bodySmall, color = D.soft)
                }
            }
            Row(Modifier.fillMaxWidth().background(D.card).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 52.dp).box(Color.Transparent, Border, 10).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Text(s.back, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = D.ink)
                }
                Box(Modifier.weight(2f).heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(D.dark).clickable { onConfirm(mode) }, contentAlignment = Alignment.Center) {
                    Text(
                        if (mode == MergeMode.REPLACE) s.moveSet(formatMoney(result, sep, cur)) else s.moveAdd(formatMoney(result, sep, cur)),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = D.onDark,
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeOption(on: Boolean, title: String, hint: String, calc: String, strike: Boolean, result: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (on) D.accentSoft else D.card)
            .border(if (on) 2.dp else 1.dp, if (on) D.accent else D.fieldBorder, shape)
            .clickable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.padding(top = 2.dp).size(20.dp).clip(RoundedCornerShape(999.dp)).border(2.dp, if (on) D.accent else D.soft, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center,
        ) { if (on) Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(D.accent)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.SemiBold, color = D.ink)
            Text(hint, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = D.soft)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    calc,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                    color = D.soft,
                    textDecoration = if (strike) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                Text(result, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold, color = D.ink)
            }
        }
    }
}

/** Whether moving into [target] needs the replace-or-add question for [ledger]'s month. */
internal fun asksHow(ledger: Ledger, target: Expense): Boolean = needsMergeChoice(target.amounts[ledger.month])
