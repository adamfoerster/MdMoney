package com.mdmoney.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatMoney
import com.mdmoney.domain.Ledger
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.theme.LocalReinoColors
import com.mdmoney.ui.theme.LocalReinoType

/** The purchases behind a ledger row: the month's one-offs for one group, and their sum. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerSheet(model: AppModel, ledger: Ledger) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val sep = LocalDecimalSeparator.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mono = TextStyle(fontFamily = LocalReinoType.current.mono, fontSize = 13.sp)

    ModalBottomSheet(onDismissRequest = { model.closeLedger() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Eyebrow("${s.month(ledger.month)} ${ledger.year}${ledger.category?.let { " · $it" } ?: ""}")
            Text(
                ledger.title,
                style = MaterialTheme.typography.displayMedium,
                color = reino.ink,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "${s.total}: ${formatMoney(ledger.total, sep)}",
                style = MaterialTheme.typography.labelLarge,
                color = reino.brass,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Eyebrow(s.entries)
            Spacer(Modifier.height(8.dp))
            if (ledger.entries.isEmpty()) {
                Text(s.noEntries, style = MaterialTheme.typography.bodyLarge, color = reino.inkSoft)
            } else {
                HairlineDivider(strong = true)
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ledger.sorted().forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().height(52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(e.date, style = mono, color = reino.inkFaint)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                e.note,
                                style = MaterialTheme.typography.bodyLarge,
                                color = reino.ink,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatMoney(e.amount, sep),
                                style = mono.copy(fontWeight = FontWeight.SemiBold),
                                color = reino.ink,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "✕",
                                style = MaterialTheme.typography.labelLarge,
                                color = reino.inkFaint,
                                modifier = Modifier.clickable { model.removeLedgerEntry(e) }.padding(8.dp),
                            )
                        }
                        HairlineDivider()
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReinoButton(s.addEntry, onClick = { model.openAddOneOff(ledger.title) }, trailingArrow = true)
                ReinoButton(s.cancel, onClick = { model.closeLedger() }, variant = ReinoButtonVariant.Ghost)
            }
        }
    }
}
