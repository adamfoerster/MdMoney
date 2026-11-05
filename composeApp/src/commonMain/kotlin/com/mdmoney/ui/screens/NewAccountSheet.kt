package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.domain.Currency
import com.mdmoney.ui.components.Icons24
import com.mdmoney.ui.components.LineIcon
import com.mdmoney.ui.openingBalanceFrom
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.LocalReinoType

/**
 * The new-account dialog, a bottom sheet: name, one of four currencies, and an optional opening
 * balance (signed by an *In credit* / *Overdrawn* toggle) that is written for the current year.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAccountSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, currency: Currency, initialBalance: Double?) -> Unit,
) {
    val s = LocalStrings.current
    val type = LocalReinoType.current
    val sep = LocalDecimalSeparator.current
    var name by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(Currency.BRL) }
    var customMode by remember { mutableStateOf(false) }
    var customSymbol by remember { mutableStateOf("") }
    val symbolFocus = remember { FocusRequester() }
    // "Custom" is a mode of its own, so it stays selected while the symbol is still blank.
    val currency = if (customMode) Currency.custom(customSymbol) else preset
    var balance by remember { mutableStateOf("") }
    var overdrawn by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = D.bg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    s.addAccount,
                    style = TextStyle(fontFamily = type.serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 32.sp),
                    color = D.ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable(role = Role.Button, onClickLabel = s.close, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons24.Close, D.ink, size = 20.dp, strokeWidth = 2f) }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Field(s.accountName) {
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        Modifier.fillMaxWidth().height(48.dp)
                            .clip(RoundedCornerShape(10.dp)).background(D.card)
                            .border(if (focused) 2.dp else 1.dp, if (focused) D.ink else Color(0xFFC9C0AF), RoundedCornerShape(10.dp))
                            .padding(horizontal = if (focused) 13.dp else 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (name.isEmpty()) Text(s.accountNameHint, fontSize = 16.sp, color = D.soft.copy(alpha = 0.6f))
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = D.ink),
                            cursorBrush = SolidColor(D.ink),
                            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).onFocusChanged { focused = it.isFocused },
                        )
                    }
                }

                Field(s.currency) {
                    val options = listOf(
                        Triple(Currency.BRL, s.currencyReal, "BRL"),
                        Triple(Currency.USD, s.currencyDollar, "USD"),
                        Triple(Currency.EUR, s.currencyEuro, "EUR"),
                        Triple(Currency.NONE, s.currencyNone, s.noSymbol),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { (c, label, sub) ->
                                    CurrencyOption(c.symbol, label, sub, selected = !customMode && preset == c, modifier = Modifier.weight(1f)) {
                                        customMode = false
                                        preset = c
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CurrencyOption(customSymbol.trim().ifEmpty { "¤" }, s.currencyCustom, s.currencySymbolLabel, selected = customMode, modifier = Modifier.weight(1f)) {
                                customMode = true
                            }
                            if (customMode) {
                                Box(
                                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).background(D.card)
                                        .border(1.dp, Color(0xFFC9C0AF), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (customSymbol.isEmpty()) Text(s.currencySymbolLabel, fontSize = 15.sp, color = D.soft.copy(alpha = 0.6f))
                                    BasicTextField(
                                        value = customSymbol,
                                        onValueChange = { customSymbol = it },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 16.sp, color = D.ink),
                                        cursorBrush = SolidColor(D.ink),
                                        modifier = Modifier.fillMaxWidth().focusRequester(symbolFocus),
                                    )
                                }
                                LaunchedEffect(Unit) { runCatching { symbolFocus.requestFocus() } }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                Field(s.initialBalance, hint = s.optional) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).background(D.card)
                                .border(1.dp, Color(0xFFC9C0AF), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (currency.hasSymbol) Text(currency.symbol, fontSize = 18.sp, color = D.soft)
                            BasicTextField(
                                value = balance,
                                onValueChange = { balance = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = D.ink, fontFeatureSettings = "tnum"),
                                cursorBrush = SolidColor(D.ink),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (balance.isEmpty()) Text("0${sep}00", fontSize = 20.sp, color = D.soft.copy(alpha = 0.6f))
                                        inner()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.bar).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            SignOption(s.balanceInCredit, selected = !overdrawn, modifier = Modifier.weight(1f)) { overdrawn = false }
                            SignOption(s.balanceOverdrawn, selected = overdrawn, modifier = Modifier.weight(1f)) { overdrawn = true }
                        }
                        Text(s.balanceLeaveBlank, fontSize = 12.sp, lineHeight = 17.sp, color = D.soft)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().background(D.card).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFB8AE9A), RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text(s.cancel, fontSize = 15.sp, color = D.ink) }
                val enabled = name.isNotBlank()
                Box(
                    Modifier.weight(2f).height(52.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (enabled) D.dark else D.dim)
                        .clickable(enabled = enabled, role = Role.Button) {
                            onCreate(name, currency, openingBalanceFrom(balance, overdrawn))
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(s.createAccount, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = D.onDark) }
            }
        }
    }
}

@Composable
private fun Field(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold, color = D.ink)
            if (hint != null) Text(" · $hint", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = D.soft)
        }
        content()
    }
}

@Composable
private fun CurrencyOption(symbol: String, label: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.height(52.dp).clip(shape)
            .background(if (selected) D.accentSoft else D.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) D.accent else D.fieldBorder, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = if (selected) 11.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) D.accent else D.bar),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                symbol.ifEmpty { "—" },
                fontSize = if (symbol.isNotEmpty()) 14.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) D.onDark else D.ink,
            )
        }
        Column {
            Text(label, fontSize = 14.sp, lineHeight = 17.sp, color = D.ink)
            Text(sub, fontSize = 11.sp, lineHeight = 13.sp, color = D.soft, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
private fun SignOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.height(36.dp).clip(shape)
            .background(if (selected) D.card else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, color = if (selected) D.ink else D.soft)
    }
}
