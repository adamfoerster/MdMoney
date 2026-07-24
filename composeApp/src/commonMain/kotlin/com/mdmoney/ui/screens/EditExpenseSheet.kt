package com.mdmoney.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatInput
import com.mdmoney.data.parseAmount
import com.mdmoney.domain.Category
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import com.mdmoney.domain.resolveCategorySlug
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.EditorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseSheet(model: AppModel, editor: EditorState, categories: List<Category> = emptyList()) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    val initial = editor.initial
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initial.title) }
    // The field speaks titles; the note stores the slug the link points at.
    var category by remember {
        mutableStateOf(initial.category?.let { slug -> categories.firstOrNull { it.slug == slug }?.title ?: slug } ?: "")
    }
    var period by remember { mutableStateOf(initial.period ?: "") }
    var type by remember { mutableStateOf(initial.type) }
    var fixedAmount by remember {
        mutableStateOf(initial.amounts.values.firstOrNull { it != null }?.let { formatInput(it, sep) } ?: "")
    }
    val eventualMonthInit = Month.ALL.firstOrNull { initial.amount(it) != null } ?: editor.defaultMonth ?: Month.ALL[0]
    var eventualMonth by remember { mutableStateOf(eventualMonthInit) }
    var eventualAmount by remember {
        mutableStateOf(initial.amount(eventualMonthInit)?.let { formatInput(it, sep) } ?: "")
    }

    ModalBottomSheet(onDismissRequest = { model.closeEditor() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            com.mdmoney.ui.components.Eyebrow(if (editor.isNew) "Nova · Registro" else "Editar · Registro")
            Text(
                when {
                    !editor.isNew -> s.editExpense
                    type == ExpenseType.INCOME -> s.addIncome
                    else -> s.addExpense
                },
                style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                color = com.mdmoney.ui.theme.LocalReinoColors.current.ink,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(s.titleLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(s.categoryLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )

            Text(s.typeLabel, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseType.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(s.typeName(t)) })
                }
            }

            OutlinedTextField(
                value = period,
                onValueChange = { period = it },
                label = { Text(s.periodLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )

            when (type) {
                ExpenseType.RECURRING_FIXED -> {
                    OutlinedTextField(
                        value = fixedAmount,
                        onValueChange = { fixedAmount = it },
                        label = { Text(s.amountLabel) },
                        prefix = if (cur.isNotEmpty()) ({ Text(cur) }) else null,
                        supportingText = { Text(s.fixedAmountHint) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
                ExpenseType.EVENTUAL -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Month.ALL.forEach { m ->
                            FilterChip(selected = eventualMonth == m, onClick = { eventualMonth = m }, label = { Text(s.monthShort(m)) })
                        }
                    }
                    OutlinedTextField(
                        value = eventualAmount,
                        onValueChange = { eventualAmount = it },
                        label = { Text(s.amountLabel) },
                        prefix = if (cur.isNotEmpty()) ({ Text(cur) }) else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
                ExpenseType.RECURRING_VARIABLE -> {
                    Text(
                        s.variableAmountHint,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                ExpenseType.INCOME -> {
                    Text(
                        s.incomeAmountHint,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            Button(
                onClick = {
                    val slug = resolveCategorySlug(category, categories)
                    model.saveExpense(editor.original, buildExpense(initial, title, slug, period, type, fixedAmount, eventualMonth, eventualAmount))
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(s.save) }
        }
    }
}

private fun buildExpense(
    initial: Expense,
    title: String,
    category: String?,
    period: String,
    type: ExpenseType,
    fixedAmount: String,
    eventualMonth: Month,
    eventualAmount: String,
): Expense {
    val amounts = initial.amounts.toMutableMap()
    when (type) {
        ExpenseType.RECURRING_FIXED -> {
            val v = parseAmount(fixedAmount)
            Month.ALL.forEach { amounts[it] = v }
        }
        ExpenseType.EVENTUAL -> {
            amounts[eventualMonth] = parseAmount(eventualAmount)
        }
        ExpenseType.RECURRING_VARIABLE,
        ExpenseType.INCOME -> { /* amounts entered per-month in the grid / on Home */ }
    }
    return initial.copy(
        title = title.trim(),
        category = category,
        period = period.trim().ifBlank { null },
        type = type,
        amounts = amounts,
    )
}
