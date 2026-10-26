package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.ui.theme.importCard
import com.mdmoney.ui.theme.box
import com.mdmoney.ui.theme.D
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatInput
import com.mdmoney.data.formatMoney
import com.mdmoney.data.parseAmount
import com.mdmoney.domain.Category
import com.mdmoney.domain.Currency
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import com.mdmoney.domain.categoryTitle
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.UiState
import com.mdmoney.ui.monthLines
import com.mdmoney.ui.components.CurrencyPicker
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.components.ReinoCheckbox
import com.mdmoney.ui.components.ReinoField
import com.mdmoney.ui.components.reinoFieldInset
import com.mdmoney.ui.theme.LocalReinoColors
import com.mdmoney.ui.theme.LocalReinoType

@Composable
fun HomeScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    var editBalance by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf(false) }

    val month = state.homeMonth
    val lines = monthLines(state.expenses, state.year, month)

    Surface(Modifier.fillMaxSize(), color = D.bg) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // --- App bar: back to the accounts, edit ---
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReinoButton("‹ ${s.accounts}", onClick = { model.switchAccount() }, variant = ReinoButtonVariant.Ghost)
                Spacer(Modifier.weight(1f))
                ReinoButton(s.editAccount, onClick = { editAccount = true }, variant = ReinoButtonVariant.Ghost)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // --- Title, currency pill, balance ---
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            state.accountTitle ?: state.selectedAccount ?: "",
                            style = MaterialTheme.typography.displayMedium,
                            color = D.ink,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            state.currency.code,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF463A6B),
                            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(D.accountBg).padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    if (state.initialBalance == null) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D.field)
                                .dashedBorder().clickable { editBalance = true }.padding(horizontal = 14.dp, vertical = 12.dp).height(36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("+", style = MaterialTheme.typography.titleMedium, color = D.ink)
                            Text(s.setInitialBalance, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium, color = D.ink)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().importCard().clickable { editBalance = true }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(s.balance.uppercase(), style = MaterialTheme.typography.labelSmall, color = D.soft)
                            Text(
                                state.balance?.let { formatMoney(it, sep, cur) } ?: "—",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp, fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Medium,
                                color = if ((state.balance ?: 0.0) < 0.0) D.red else D.ink,
                            )
                            Text(
                                "${s.initialBalance}: ${formatMoney(state.initialBalance, sep, cur)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = D.soft,
                            )
                        }
                    }
                }

                MonthStepper(month, s.month(month), state.year) { model.setHomeMonth(it) }

                if (lines.isEmpty) {
                    Text(
                        s.nothingThisMonth,
                        style = MaterialTheme.typography.bodyLarge,
                        color = D.soft,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Section(s.incomeSection, lines.income, month, D.teal, model, state.categories)
                    Section(s.recurringSection, lines.recurring, month, D.ink, model, state.categories)
                    Section(s.eventualSection, lines.eventual, month, D.ink, model, state.categories)
                }

                // --- Actions ---
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile(s.addOneOff, dark = true, modifier = Modifier.weight(1f)) { model.openAddOneOff() }
                    ActionTile(s.addIncome, dark = false, modifier = Modifier.weight(1f)) { model.openAddIncome() }
                    if (state.canImport) {
                        ActionTile(s.importStatement, dark = false, modifier = Modifier.weight(1f)) { model.openImport() }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (editBalance) {
        BalanceDialog(
            current = state.initialBalance,
            onDismiss = { editBalance = false },
            onSave = { model.setInitialBalance(it); editBalance = false },
        )
    }

    if (editAccount) {
        EditAccountDialog(
            title = state.accountTitle ?: state.selectedAccount.orEmpty(),
            currency = state.currency,
            onDismiss = { editAccount = false },
            onSave = { title, currency -> model.editAccount(title, currency); editAccount = false },
        )
    }
}

@Composable
private fun ActionTile(label: String, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(76.dp)
            .box(if (dark) D.dark else D.card, if (dark) D.dark else D.fieldBorder, 12)
            .clickable(onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
            fontWeight = FontWeight.Medium,
            color = if (dark) D.onDark else D.ink,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.dashedBorder(): Modifier = drawBehind {
    val stroke = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
    drawRoundRect(Color(0xFFB8AE9A), style = stroke, cornerRadius = CornerRadius(12.dp.toPx()))
}

/**
 * One labelled block of the month's lines, with the block's own total for the month. Renders nothing
 * when it has no lines, so an account without income (or without any one-off yet) shows no empty
 * heading.
 */
@Composable
private fun Section(
    label: String,
    rows: List<Expense>,
    month: Month,
    accent: Color,
    model: AppModel,
    categories: List<Category>,
) {
    if (rows.isEmpty()) return
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val subtotal = rows.sumOf { it.amount(month) ?: 0.0 }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Top) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = D.soft, modifier = Modifier.weight(1f))
            Text(
                formatMoney(subtotal, sep, cur),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                color = accent,
            )
        }
        Column(Modifier.fillMaxWidth().importCard()) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                BillRow(model, row, month, categories)
            }
        }
    }
}

@Composable
private fun MonthStepper(month: Month, label: String, year: Int, onSelect: (Month) -> Unit) {
    fun shift(delta: Int) {
        val n = ((month.number - 1 + delta + 12) % 12) + 1
        onSelect(Month.ALL.first { it.number == n })
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D.dark).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(44.dp).clickable { shift(-1) }, contentAlignment = Alignment.Center) {
            Text("‹", style = MaterialTheme.typography.titleLarge, color = D.onDark)
        }
        Text("$label $year".uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = D.onDark)
        Box(Modifier.size(44.dp).clickable { shift(1) }, contentAlignment = Alignment.Center) {
            Text("›", style = MaterialTheme.typography.titleLarge, color = D.onDark)
        }
    }
}

/** The money column. Fixed, so every figure in the list lands on the same right edge. */
private val AmountColumn = 112.dp

/** Stands in for a month with no value — the design system's own empty mark. */
private const val NoValue = "—"

@Composable
private fun BillRow(model: AppModel, expense: Expense, month: Month, categories: List<Category>) {
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val paid = expense.isPaid(month)
    val accent = if (expense.isIncome) D.teal else D.ink
    val numberStyle = TextStyle(
        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
        fontSize = 15.sp,
        fontFeatureSettings = "tnum",
        textAlign = TextAlign.End,
    )
    // Only a variable line is typed into; a fixed bill, a one-off and a ledger's sum are all read
    // from elsewhere. The dashed rule under a field says exactly this, so the two must agree.
    val editable = expense.type == ExpenseType.RECURRING_VARIABLE || expense.isIncome

    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A ledger's money is already spent, so its check is a statement of fact, not a toggle.
            ReinoCheckbox(
                checked = paid,
                onToggle = if (expense.ledger) null else ({ model.togglePaidThisMonth(expense) }),
                accent = accent,
            )
            Column(
                Modifier.weight(1f)
                    .clickable { if (expense.ledger) model.openLedger(expense) else model.openEdit(expense) }
                    .padding(start = 4.dp),
            ) {
                Text(expense.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium, color = D.ink, maxLines = 1)
                // The section heading already names the kind, so the subtitle is just the category —
                // named as its own note names it, not as the slug the link points at.
                categoryTitle(expense.category, categories)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = D.soft,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (editable) {
                VariableAmountField(expense, month, numberStyle, accent) {
                    model.setVariableAmountThisMonth(expense, it)
                }
            } else {
                val amount = expense.amount(month)
                Text(
                    amount?.let { formatMoney(it, sep, cur) } ?: NoValue,
                    style = numberStyle.copy(fontWeight = FontWeight.Medium),
                    color = when {
                        amount == null -> reino.inkFaint
                        expense.isIncome -> D.teal
                        else -> D.ink
                    },
                    // Padded like the field beside it, so a printed figure and a typed one share a line.
                    modifier = Modifier.width(AmountColumn).reinoFieldInset(),
                )
            }
        }
    }
}

@Composable
private fun VariableAmountField(
    expense: Expense,
    month: Month,
    numberStyle: TextStyle,
    accent: Color,
    onCommit: (Double?) -> Unit,
) {
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    // Local text, re-seeded whenever the underlying value changes; commits on focus loss.
    var text by remember(expense.id, month, expense.amount(month), sep) {
        mutableStateOf(expense.amount(month)?.let { formatInput(it, sep) } ?: "")
    }
    ReinoField(
        value = text,
        onValueChange = { text = it },
        textStyle = numberStyle.copy(color = if (expense.isIncome) accent else LocalReinoColors.current.ink),
        placeholder = NoValue,
        contentAlignment = Alignment.CenterEnd,
        keyboardType = KeyboardType.Decimal,
        prefix = cur,
        onFocusLost = {
            val committed = parseAmount(text)
            if (committed != expense.amount(month)) onCommit(committed)
        },
        modifier = Modifier.width(AmountColumn),
    )
}

@Composable
private fun BalanceDialog(current: Double?, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    var text by remember { mutableStateOf(current?.let { formatInput(it, sep) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.setInitialBalance, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(s.initialBalance) },
                prefix = if (cur.isNotEmpty()) ({ Text(cur) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(parseAmount(text)) }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

/** Edits the account's display name and currency; the opening balance stays its own inline control. */
@Composable
private fun EditAccountDialog(
    title: String,
    currency: Currency,
    onDismiss: () -> Unit,
    onSave: (String, Currency) -> Unit,
) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    var name by remember { mutableStateOf(title) }
    var picked by remember { mutableStateOf(currency) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.editAccount, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(s.accountName) },
                )
                Text(
                    s.currency,
                    style = MaterialTheme.typography.labelLarge,
                    color = reino.inkSoft,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                CurrencyPicker(selected = picked, onSelect = { picked = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, picked) }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
