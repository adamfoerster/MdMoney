package com.mdmoney.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.mdmoney.LocalCurrencySymbol
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatInput
import com.mdmoney.data.parseAmount
import com.mdmoney.domain.Expense
import com.mdmoney.domain.Month

@Composable
fun CellEditDialog(
    expense: Expense,
    month: Month,
    onDismiss: () -> Unit,
    onSave: (amount: Double?, paid: Boolean) -> Unit,
) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val cur = LocalCurrencySymbol.current
    var text by remember { mutableStateOf(expense.amount(month)?.let { formatInput(it, sep) } ?: "") }
    var paid by remember { mutableStateOf(expense.isPaid(month)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${expense.title} · ${s.month(month)}") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(s.amountLabel) },
                    prefix = if (cur.isNotEmpty()) ({ Text(cur) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = paid, onCheckedChange = { paid = it })
                    Text(s.paid, modifier = Modifier.padding(start = 4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(parseAmount(text), paid) }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
