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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalStrings
import com.mdmoney.data.parseAmount
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.OneOffState
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.theme.LocalReinoColors

/**
 * Records one purchase. It lands as a row in `<year> <Mon> - <group>.md` rather than a note of its
 * own, so a month of coffees stays one file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneOffSheet(model: AppModel, state: OneOffState) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var group by remember { mutableStateOf(state.group) }
    var category by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(state.date) }
    var note by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = { model.closeOneOff() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Eyebrow("${s.thisMonth} · ${s.month(state.month)}")
            Text(
                s.addOneOff,
                style = MaterialTheme.typography.displayMedium,
                color = reino.ink,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                s.oneOffHint,
                style = MaterialTheme.typography.bodySmall,
                color = reino.inkSoft,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = group,
                onValueChange = { group = it },
                label = { Text(s.groupLabel) },
                supportingText = { Text(s.groupHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            // Reuse a group already in this account instead of retyping it.
            if (state.knownGroups.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.knownGroups.forEach { g ->
                        FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) })
                    }
                }
            }

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(s.categoryLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            if (state.knownCategories.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.knownCategories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
            }

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
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )

            Button(
                onClick = { model.saveOneOff(group, category, date, note, parseAmount(amount)) },
                enabled = group.isNotBlank() && parseAmount(amount) != null && date.length == 8,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(s.save) }
        }
    }
}
