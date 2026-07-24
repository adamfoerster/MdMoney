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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatInput
import com.mdmoney.data.formatMoney
import com.mdmoney.data.parseAmount
import com.mdmoney.domain.Category
import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.categoryTitle
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.components.SectionBand
import com.mdmoney.ui.formatEntryDate
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
fun LedgerSheet(model: AppModel, ledger: Ledger, categories: List<Category> = emptyList()) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val type = LocalReinoType.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = ledger.sorted()
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }

    val figure = TextStyle(fontFamily = type.mono, fontSize = 14.sp, letterSpacing = 0.02.em)
    val description = TextStyle(
        fontFamily = type.serif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    )

    ModalBottomSheet(onDismissRequest = { model.closeLedger() }, sheetState = sheetState) {
        // One scroll for the whole book, as the design system's page reads: the sum's band closes
        // the entries rather than floating over them. Scrolling only the rows would push the band
        // and the actions off the bottom of a long month.
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // --- Masthead: what this book is, and how many entries it holds ---
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    val category = categoryTitle(ledger.category, categories)
                    Eyebrow("${s.month(ledger.month)} ${ledger.year}${category?.let { " · $it" } ?: ""}")
                    Text(
                        ledger.title,
                        style = MaterialTheme.typography.displayMedium,
                        color = reino.ink,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                // Counting is part of the brand: an archive says how much it holds, zero-padded.
                Text(
                    "${entries.size.toString().padStart(2, '0')} ${s.entries}".uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = reino.inkFaint,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            // The masthead closes on the heavy ink rule, not a hairline.
            Row(Modifier.fillMaxWidth().height(2.dp).background(reino.ink)) {}

            if (entries.isEmpty()) {
                Text(
                    s.noEntries,
                    style = MaterialTheme.typography.bodyLarge,
                    color = reino.inkSoft,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp)) {
                    ColumnHead(s.numberLabel, Modifier.width(OrdinalWidth))
                    ColumnHead(s.noteLabel, Modifier.weight(1f))
                    ColumnHead(s.amountLabel, Modifier.width(96.dp), TextAlign.End)
                }
                Column {
                    entries.forEachIndexed { index, entry ->
                        HairlineDivider()
                        // The whole row opens the entry to edit or remove; a purchase is a line you
                        // correct in place, not only one you can strike out.
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = entry }.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                (index + 1).toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelLarge,
                                color = reino.brass,
                                modifier = Modifier.width(OrdinalWidth).padding(top = 3.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(entry.note, style = description, color = reino.ink, maxLines = 2)
                                Text(
                                    formatEntryDate(entry.date, s),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = reino.inkFaint,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Text(
                                formatMoney(entry.amount, sep, cur),
                                style = figure.copy(fontWeight = FontWeight.SemiBold),
                                color = reino.ink,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(96.dp).padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            // --- The sum, carried by the brass band ---
            Spacer(Modifier.height(20.dp))
            SectionBand(title = s.total, number = formatMoney(ledger.total, sep, cur))

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReinoButton(
                    s.addEntry,
                    onClick = { model.openAddOneOff(ledger.title, ledger.month, reopenLedger = true) },
                    trailingArrow = true,
                )
                ReinoButton(s.cancel, onClick = { model.closeLedger() }, variant = ReinoButtonVariant.Ghost)
            }
        }
    }

    editing?.let { entry ->
        EditLedgerEntryDialog(
            entry = entry,
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
            Column {
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
