package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatInput
import com.mdmoney.data.formatMoney
import com.mdmoney.data.parseAmount
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.UiState
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.theme.LocalReinoColors
import com.mdmoney.ui.theme.LocalReinoType

@Composable
fun HomeScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    var editBalance by remember { mutableStateOf(false) }

    val month = state.homeMonth
    // This month's lines: income and recurring items always, plus one-offs that land in the month.
    // Income sorts first — money in, then money out.
    val bills = state.expenses
        .filter { it.year == state.year }
        .filter {
            it.isIncome ||
                it.type == ExpenseType.RECURRING_FIXED ||
                it.type == ExpenseType.RECURRING_VARIABLE ||
                it.amount(month) != null
        }
        .sortedWith(compareBy({ !it.isIncome }, { it.title.lowercase() }))

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
                Eyebrow("${state.selectedAccount ?: ""} · ${s.balance}")
                Spacer(Modifier.height(6.dp))
                Text(
                    state.balance?.let { formatMoney(it, sep) } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    color = if ((state.balance ?: 0.0) < 0.0) reino.brassDeep else reino.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.initialBalance != null) "${s.initialBalance}: ${formatMoney(state.initialBalance, sep)}" else s.setInitialBalance,
                    style = MaterialTheme.typography.labelMedium,
                    color = reino.brass,
                    modifier = Modifier.clickable { editBalance = true }.padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(20.dp))
            }

            MonthStepper(month, s.month(month), state.year) { model.setHomeMonth(it) }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(8.dp))
                Eyebrow(s.thisMonth)
                Spacer(Modifier.height(8.dp))
                if (bills.isEmpty()) {
                    Text(
                        s.nothingThisMonth,
                        style = MaterialTheme.typography.bodyLarge,
                        color = reino.inkSoft,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    HairlineDivider(strong = true)
                    bills.forEach { BillRow(model, it, month) }
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

@Composable
private fun BillRow(model: AppModel, expense: Expense, month: Month) {
    val reino = LocalReinoColors.current
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val paid = expense.isPaid(month)
    val numberStyle = TextStyle(fontFamily = LocalReinoType.current.mono, fontSize = 14.sp, letterSpacing = 0.02.em)

    Column {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A ledger's money is already spent, so its check is a statement of fact, not a toggle.
            Checkbox(
                checked = paid,
                onCheckedChange = if (expense.ledger) null else ({ model.togglePaidThisMonth(expense) }),
                enabled = !expense.ledger,
                colors = CheckboxDefaults.colors(
                    checkedColor = if (expense.isIncome) reino.verdigris else reino.brass,
                    uncheckedColor = reino.inkFaint,
                    checkmarkColor = reino.onBrass,
                    disabledCheckedColor = reino.brass,
                ),
            )
            Column(
                Modifier.weight(1f)
                    .clickable { if (expense.ledger) model.openLedger(expense) else model.openEdit(expense) }
                    .padding(start = 4.dp),
            ) {
                Text(expense.title, style = MaterialTheme.typography.bodyLarge, color = reino.ink, maxLines = 1)
                // Income is called out by name on the subtitle line; expenses just show their category.
                val subtitle = if (expense.isIncome) {
                    listOfNotNull(s.typeName(ExpenseType.INCOME), expense.category).joinToString(" · ")
                } else {
                    expense.category
                }
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (expense.isIncome) reino.verdigris else reino.inkFaint,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (expense.type == ExpenseType.RECURRING_VARIABLE || expense.isIncome) {
                VariableAmountField(expense, month, expense.isIncome) {
                    model.setVariableAmountThisMonth(expense, it)
                }
            } else {
                Text(
                    expense.amount(month)?.let { formatMoney(it, sep) } ?: "·",
                    style = numberStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = if (paid) reino.brass else reino.ink,
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
    isIncome: Boolean = false,
    onCommit: (Double?) -> Unit,
) {
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    // Local text, re-seeded whenever the underlying value changes; commits on focus loss.
    var text by remember(expense.id, month, expense.amount(month), sep) {
        mutableStateOf(expense.amount(month)?.let { formatInput(it, sep) } ?: "")
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text("·", color = reino.inkFaint) },
        textStyle = TextStyle(
            fontFamily = LocalReinoType.current.mono,
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            color = if (isIncome) reino.verdigris else reino.ink,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(112.dp).onFocusChanged { focus ->
            if (!focus.isFocused) {
                val committed = parseAmount(text)
                if (committed != expense.amount(month)) onCommit(committed)
            }
        },
    )
}

@Composable
private fun BalanceDialog(current: Double?, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(parseAmount(text)) }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
