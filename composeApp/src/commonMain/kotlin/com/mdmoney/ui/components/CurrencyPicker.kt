package com.mdmoney.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalStrings
import com.mdmoney.domain.Currency

/**
 * Picks an account's [Currency]: a row of chips (none, the three presets, custom) and — when custom
 * is chosen — a field for the symbol to type. Used both when creating an account and when editing one.
 *
 * "Custom" is a UI mode of its own rather than being inferred from the value, so the field stays open
 * (and the chip stays lit) even while the symbol is still blank; an empty custom symbol simply reads
 * back as [Currency.NONE].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPicker(
    selected: Currency,
    onSelect: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    var customMode by remember { mutableStateOf(selected.isCustom) }
    var customSymbol by remember { mutableStateOf(if (selected.isCustom) selected.symbol else "") }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            @Composable
            fun preset(currency: Currency, label: String) {
                FilterChip(
                    selected = !customMode && selected == currency,
                    onClick = { customMode = false; onSelect(currency) },
                    label = { Text(label) },
                )
            }
            preset(Currency.NONE, s.currencyNone)
            preset(Currency.BRL, "${s.currencyReal} (${Currency.BRL.symbol})")
            preset(Currency.EUR, "${s.currencyEuro} (${Currency.EUR.symbol})")
            preset(Currency.USD, "${s.currencyDollar} (${Currency.USD.symbol})")
            FilterChip(
                selected = customMode,
                onClick = { customMode = true; onSelect(Currency.custom(customSymbol)) },
                label = { Text(s.currencyCustom) },
            )
        }

        if (customMode) {
            OutlinedTextField(
                value = customSymbol,
                onValueChange = { customSymbol = it; onSelect(Currency.custom(it)) },
                label = { Text(s.currencySymbolLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
