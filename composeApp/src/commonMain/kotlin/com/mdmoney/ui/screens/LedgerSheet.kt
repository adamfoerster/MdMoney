package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.ui.theme.box
import com.mdmoney.ui.theme.D
import com.mdmoney.domain.Month
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatInput
import com.mdmoney.data.formatMoney
import com.mdmoney.data.parseAmount
import com.mdmoney.domain.Category
import com.mdmoney.domain.Expense
import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.MergeMode
import com.mdmoney.domain.categoryTitle
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.Icons24
import com.mdmoney.ui.components.LineIcon
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.components.SectionBand
import com.mdmoney.ui.components.chipRowScroll
import com.mdmoney.ui.formatEntryDate
import com.mdmoney.ui.mostUsedCategories
import com.mdmoney.ui.theme.LocalReinoColors
import com.mdmoney.ui.theme.LocalReinoType

private val OrdinalWidth = 30.dp

/**
 * The purchases behind a ledger row: the month's one-offs for one group, and their sum.
 *
 * Laid out as the design system's `Ledger`: a masthead closed by a heavy ink rule, mono column
 * heads, numbered rows (brass ordinal · serif description · mono meta · mono figure), and the sum
 * carried by a brass band — the system's signature motif — rather than a line of small text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerSheet(model: AppModel, ledger: Ledger, categories: List<Category> = emptyList(), expenses: List<Expense> = emptyList()) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val type = LocalReinoType.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = ledger.sorted()
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    // The pencil turns the entries into something to act on: tap one for move, edit and delete.
    var editMode by remember { mutableStateOf(false) }
    var name by remember(ledger.title) { mutableStateOf(ledger.title) }
    // Saves a changed name; an unchanged or blank one is dropped back to the stored title.
    fun commitName() {
        val typed = name.trim()
        if (typed.isNotBlank() && typed != ledger.title) model.renameLedger(typed) else name = ledger.title
    }
    var pickingCategory by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<LedgerEntry?>(null) }
    var moving by remember { mutableStateOf<LedgerEntry?>(null) }
    var merging by remember { mutableStateOf<Pair<LedgerEntry, Expense>?>(null) }

    val figure = TextStyle(fontFamily = type.mono, fontSize = 14.sp, letterSpacing = 0.02.em)
    val description = TextStyle(
        fontFamily = type.serif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    )

    ModalBottomSheet(
        onDismissRequest = { model.closeLedger() },
        sheetState = sheetState,
        containerColor = D.bg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        // One scroll for the whole sheet: the entries, then the actions, so a long month never
        // pushes the buttons off the bottom.
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            // --- Header: month · category, group name ---
            val category = categoryTitle(ledger.category, categories)
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${s.month(ledger.month)} ${ledger.year}${category?.takeUnless { editMode }?.let { " · $it" } ?: ""}".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = D.soft,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    if (editMode) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.displayMedium.copy(fontSize = 26.sp, lineHeight = 30.sp, color = D.ink),
                            cursorBrush = SolidColor(D.ink),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitName() }),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).onFocusChanged { if (!it.isFocused) commitName() },
                            decorationBox = { inner ->
                                Box(
                                    Modifier.fillMaxWidth().height(44.dp).box(D.field, D.fieldBorder, 8).padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) { inner() }
                            },
                        )
                    } else {
                        Text(
                            ledger.title,
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp, lineHeight = 34.sp),
                            color = D.ink,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                        )
                    }
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (editMode) D.dark else Color.Transparent)
                        .clickable { commitName(); editMode = !editMode },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons24.Pencil, if (editMode) D.onDark else D.ink, size = 20.dp) }
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable { commitName(); model.closeLedger() }, contentAlignment = Alignment.Center) {
                    LineIcon(Icons24.Close, D.ink, size = 20.dp, strokeWidth = 2f)
                }
            }

            // In edit mode the category is a control: it opens the same picker as the expense form.
            if (editMode) {
                Row(
                    Modifier.padding(start = 4.dp, top = 10.dp).height(36.dp)
                        .box(if (category == null) D.todo else D.field, if (category == null) D.todoBorder else D.fieldBorder, 999)
                        .clickable { pickingCategory = true }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(category ?: s.uncategorized, style = MaterialTheme.typography.bodyMedium, color = D.ink, maxLines = 1)
                    LineIcon(Icons24.ChevronDown, D.soft, size = 14.dp, strokeWidth = 2f)
                }
            }

            // --- Total ---
            Row(
                Modifier.padding(top = 14.dp, bottom = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(D.dark).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.total.uppercase(), style = MaterialTheme.typography.labelSmall, color = D.dim)
                    Text(
                        "${entries.size} ${s.entries}",
                        style = MaterialTheme.typography.bodySmall,
                        color = D.dim,
                    )
                }
                Text(
                    formatMoney(ledger.total, sep, cur),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp, fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    color = D.onDark,
                )
            }

            if (entries.isEmpty()) {
                Text(
                    s.noEntries,
                    style = MaterialTheme.typography.bodyLarge,
                    color = D.soft,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                // In edit mode the whole row opens the entry's actions (move, edit, delete).
                entries.forEach { entry ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = editMode) { acting = entry }.heightIn(min = 68.dp).padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val day = entry.date.takeLast(2).trimStart('0')
                        val monthNo = entry.date.drop(4).take(2).toIntOrNull()
                        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium, color = D.ink)
                            Month.ALL.firstOrNull { it.number == monthNo }?.let {
                                Text(s.monthShort(it).uppercase(), style = MaterialTheme.typography.labelSmall, color = D.soft)
                            }
                        }
                        Text(
                            entry.note,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Medium,
                            color = D.ink,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatMoney(entry.amount, sep, cur),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium,
                            color = D.ink,
                            textAlign = TextAlign.End,
                        )
                        if (editMode) LineIcon(Icons24.Dots, D.soft, size = 18.dp, strokeWidth = 3f)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                if (editMode) {
                    Text(
                        s.tapEntryHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = D.soft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }

            // --- Actions ---
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).box(Color.Transparent, Color(0xFFB8AE9A), 10).clickable { model.closeLedger() },
                    contentAlignment = Alignment.Center,
                ) { Text(s.cancel, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = D.ink) }
                Box(
                    Modifier.weight(2f).height(52.dp).clip(RoundedCornerShape(10.dp)).background(D.dark)
                        .clickable { model.openAddOneOff(ledger.title, ledger.month, reopenLedger = true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+  ${s.addEntry}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium, color = D.onDark)
                }
            }
        }
    }

    if (pickingCategory) {
        ModalBottomSheet(
            onDismissRequest = { pickingCategory = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = D.bg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            CategoryPickerPage(
                categories = categories,
                mostUsed = mostUsedCategories(expenses),
                selected = ledger.category,
                onPick = { model.setLedgerCategory(it); pickingCategory = false },
                onBack = { pickingCategory = false },
                noneLabel = s.uncategorized,
            )
        }
    }

    acting?.let { entry ->
        EntryActionsSheet(
            ledger = ledger,
            entry = entry,
            onMove = { moving = entry; acting = null },
            onEdit = { editing = entry; acting = null },
            onDelete = { model.removeLedgerEntry(entry); acting = null },
            onDismiss = { acting = null },
        )
    }

    moving?.let { entry ->
        MoveEntrySheet(
            ledger = ledger,
            entry = entry,
            expenses = expenses,
            categories = categories,
            onBack = { moving = null; acting = entry },
            onMoveToOneOff = { title -> model.moveLedgerEntry(entry, title); moving = null },
            onPickRecurring = { target ->
                if (asksHow(ledger, target)) {
                    merging = entry to target
                } else {
                    model.moveLedgerEntryToRecurring(entry, target, MergeMode.REPLACE)
                    moving = null
                }
            },
        )
    }

    merging?.let { (entry, target) ->
        MergeChoiceSheet(
            ledger = ledger,
            entry = entry,
            target = target,
            onBack = { merging = null },
            onConfirm = { mode ->
                model.moveLedgerEntryToRecurring(entry, target, mode)
                merging = null
                moving = null
            },
        )
    }

    editing?.let { entry ->
        EditLedgerEntryDialog(
            entry = entry,
            onUngroup = { model.ungroupLedgerEntry(entry); editing = null },
            onDismiss = { editing = null },
            onRemove = { model.removeLedgerEntry(entry); editing = null },
            onSave = { updated -> model.updateLedgerEntry(entry, updated); editing = null },
        )
    }
}

/**
 * Edits one purchase in a ledger — its date, note and amount — or removes it. The date keeps the raw
 * `yyyyMMdd` the file stores; the amount is typed with the account's separator and never carries the
 * currency symbol into the value itself.
 */
@Composable
private fun EditLedgerEntryDialog(
    entry: LedgerEntry,
    onUngroup: () -> Unit,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onSave: (LedgerEntry) -> Unit,
) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    var date by remember { mutableStateOf(entry.date) }
    var note by remember { mutableStateOf(entry.note) }
    var amount by remember { mutableStateOf(formatInput(entry.amount, sep)) }

    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.editEntry, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text("${s.dateLabel} (AAAAMMDD)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(s.noteLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(s.amountLabel) },
                    prefix = if (cur.isNotEmpty()) ({ Text(cur) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                TextButton(onClick = onUngroup, modifier = Modifier.padding(top = 4.dp)) { Text(s.moveOutOfGroup) }
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text(s.remove, color = LocalReinoColors.current.brassDeep) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && date.length == 8,
                onClick = { onSave(entry.copy(date = date, note = note.trim(), amount = parsed!!)) },
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

/**
 * Moves a purchase between groups: a chip per group it could go to, one that takes it out of any
 * group ([onOut], when it is in one), and a field for a group that doesn't exist yet.
 */
@Composable
internal fun MoveToGroup(groups: List<String>, onMove: (String) -> Unit, onOut: (() -> Unit)? = null) {
    val s = LocalStrings.current
    var fresh by remember { mutableStateOf("") }
    Text(
        s.moveToGroup,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
    Row(Modifier.chipRowScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        onOut?.let { FilterChip(selected = false, onClick = it, label = { Text(s.groupNone) }) }
        groups.forEach { g -> FilterChip(selected = false, onClick = { onMove(g) }, label = { Text(g) }) }
    }
    OutlinedTextField(
        value = fresh,
        onValueChange = { fresh = it },
        label = { Text(s.newGroupLabel) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
    TextButton(enabled = fresh.isNotBlank(), onClick = { onMove(fresh.trim()) }) { Text(s.moveToGroup) }
}

/** Mono, uppercase, faint — the ledger's column heads. */
@Composable
private fun ColumnHead(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalReinoColors.current.inkFaint,
        textAlign = align,
        modifier = modifier,
    )
}
