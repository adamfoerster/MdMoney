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

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // --- Header: switch account + balance ---
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReinoButton("‹ ${s.accounts}", onClick = { model.switchAccount() }, variant = ReinoButtonVariant.Ghost)
                    Spacer(Modifier.weight(1f))
                    Eyebrow(state.year.toString(), color = reino.inkFaint)
                }
                Spacer(Modifier.height(18.dp))
                Eyebrow("${state.accountTitle ?: state.selectedAccount ?: ""} · ${s.balance}")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.balance?.let { formatMoney(it, sep, cur) } ?: "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = if ((state.balance ?: 0.0) < 0.0) reino.brassDeep else reino.ink,
                        modifier = Modifier.weight(1f),
                    )
                    ReinoButton(s.editAccount, onClick = { editAccount = true }, variant = ReinoButtonVariant.Ghost)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.initialBalance != null) "${s.initialBalance}: ${formatMoney(state.initialBalance, sep, cur)}" else s.setInitialBalance,
                    style = MaterialTheme.typography.labelMedium,
                    color = reino.brass,
                    modifier = Modifier.clickable { editBalance = true }.padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(20.dp))
            }

            MonthStepper(month, s.month(month), state.year) { model.setHomeMonth(it) }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(8.dp))
                if (lines.isEmpty) {
                    Eyebrow(s.thisMonth)
                    Text(
                        s.nothingThisMonth,
                        style = MaterialTheme.typography.bodyLarge,
                        color = reino.inkSoft,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    Section(s.incomeSection, lines.income, month, reino.verdigris, model, state.categories)
                    Section(s.recurringSection, lines.recurring, month, reino.ink, model, state.categories)
                    Section(s.eventualSection, lines.eventual, month, reino.ink, model, state.categories)
                }
                Spacer(Modifier.height(24.dp))
                ReinoButton(s.addOneOff, onClick = { model.openAddOneOff() }, trailingArrow = true)
                Spacer(Modifier.height(10.dp))
                ReinoButton(
                    s.addIncome,
                    onClick = { model.openAddIncome() },
                    variant = ReinoButtonVariant.Secondary,
                    trailingArrow = true,
                )
                Spacer(Modifier.height(28.dp))
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
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val subtotal = rows.sumOf { it.amount(month) ?: 0.0 }

    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(label)
        Spacer(Modifier.weight(1f))
        Text(
            formatMoney(subtotal, sep, cur),
            style = TextStyle(
                fontFamily = LocalReinoType.current.mono,
                fontSize = 12.sp,
                letterSpacing = 0.02.em,
                textAlign = TextAlign.End,
            ),
            color = accent,
            // The same column the rows below use, so the block's sum sits over its own figures.
            modifier = Modifier.width(AmountColumn).reinoFieldInset(),
        )
    }
    HairlineDivider(strong = true)
    rows.forEach { BillRow(model, it, month, categories) }
}

@Composable
private fun MonthStepper(month: Month, label: String, year: Int, onSelect: (Month) -> Unit) {
    val reino = LocalReinoColors.current
    fun shift(delta: Int) {
        val n = ((month.number - 1 + delta + 12) % 12) + 1
        onSelect(Month.ALL.first { it.number == n })
    }
    Row(
        Modifier.fillMaxWidth().background(reino.brassBand).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("‹", style = MaterialTheme.typography.titleLarge, color = reino.onBrass, modifier = Modifier.clickable { shift(-1) }.padding(horizontal = 8.dp))
        Text("$label $year".uppercase(), style = MaterialTheme.typography.labelLarge, color = reino.onBrass)
        Text("›", style = MaterialTheme.typography.titleLarge, color = reino.onBrass, modifier = Modifier.clickable { shift(1) }.padding(horizontal = 8.dp))
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
    val accent = if (expense.isIncome) reino.verdigris else reino.brass
    val numberStyle = TextStyle(
        fontFamily = LocalReinoType.current.mono,
        fontSize = 14.sp,
        letterSpacing = 0.02.em,
        textAlign = TextAlign.End,
    )
    // Only a variable line is typed into; a fixed bill, a one-off and a ledger's sum are all read
    // from elsewhere. The dashed rule under a field says exactly this, so the two must agree.
    val editable = expense.type == ExpenseType.RECURRING_VARIABLE || expense.isIncome

    Column {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
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
                Text(expense.title, style = MaterialTheme.typography.bodyLarge, color = reino.ink, maxLines = 1)
                // The section heading already names the kind, so the subtitle is just the category —
                // named as its own note names it, not as the slug the link points at.
                categoryTitle(expense.category, categories)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (expense.isIncome) reino.verdigris else reino.inkFaint,
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
                    style = numberStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        amount == null -> reino.inkFaint
                        paid -> reino.brass
                        else -> reino.ink
                    },
                    // Padded like the field beside it, so a printed figure and a typed one share a line.
                    modifier = Modifier.width(AmountColumn).reinoFieldInset(),
                )
            }
        }
        HairlineDivider()
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
