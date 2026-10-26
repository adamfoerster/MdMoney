package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatMoney
import com.mdmoney.data.parseAmount
import com.mdmoney.domain.Currency
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.ui.AccountSummary
import com.mdmoney.ui.AppModel
import com.mdmoney.ui.HomeTab
import com.mdmoney.ui.UiState
import com.mdmoney.ui.components.CurrencyPicker
import com.mdmoney.ui.components.Icons24
import com.mdmoney.ui.components.LineIcon
import com.mdmoney.ui.components.ReinoCheckbox
import com.mdmoney.ui.components.ReinoField
import com.mdmoney.ui.components.ReinoTabBar
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.LocalReinoColors
import com.mdmoney.ui.theme.LocalReinoType

/** Avatar tints (background, letter), handed out to the accounts in order. */
private val Avatars = listOf(
    Color(0xFFECE6F7) to Color(0xFF463A6B),
    Color(0xFFE9DDF2) to Color(0xFF4D2A66),
    Color(0xFFF6E2CF) to Color(0xFF7A3F10),
    Color(0xFFDCEAE5) to Color(0xFF23473F),
)

/** What a hidden figure shows instead of its digits. */
private const val MASK = "••••"

@Composable
fun AccountsScreen(model: AppModel, state: UiState) {
    val s = LocalStrings.current
    val type = LocalReinoType.current
    var showAdd by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = D.bg) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${s.appName} · Arquivo".uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = D.soft,
                        )
                        Text(
                            s.accounts,
                            style = TextStyle(fontFamily = type.serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 36.sp),
                            color = D.ink,
                        )
                    }
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).background(D.field)
                            .border(1.dp, D.fieldBorder, RoundedCornerShape(999.dp))
                            .clickable { model.setHideAmounts(!state.hideAmounts) },
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(if (state.hideAmounts) Icons24.EyeOff else Icons24.Eye, D.ink, size = 20.dp)
                    }
                }

                if (state.accounts.isEmpty()) {
                    Text(s.noAccounts, style = MaterialTheme.typography.bodyLarge, color = D.soft, modifier = Modifier.padding(vertical = 12.dp))
                }
                state.accounts.forEachIndexed { i, account ->
                    AccountCard(model, account, state.summaries[account], Avatars[i % Avatars.size], state.hideAmounts)
                }

                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                        .dashedBox(Color(0xFFB8AE9A), 14).clickable { showAdd = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    LineIcon(Icons24.Plus, D.ink, size = 18.dp, strokeWidth = 2f)
                    Text(s.addAccount, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.Medium, color = D.ink)
                }
            }
            // Annual and Reports read one account, so they wait until one is open.
            ReinoTabBar(
                current = HomeTab.HOME,
                onSelect = { if (it == HomeTab.SETTINGS) model.openSettings() },
                enabled = { it == HomeTab.HOME || it == HomeTab.SETTINGS },
            )
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var currency by remember { mutableStateOf(Currency.NONE) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(s.addAccount, style = MaterialTheme.typography.titleLarge) },
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
                        color = D.soft,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    CurrencyPicker(selected = currency, onSelect = { currency = it })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { model.createAccount(name, currency); showAdd = false }) {
                    Text(s.create)
                }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(s.cancel) } },
        )
    }
}

internal fun Modifier.dashedBox(color: Color, radius: Int): Modifier = drawBehind {
    val stroke = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
    drawRoundRect(color, style = stroke, cornerRadius = CornerRadius(radius.dp.toPx()))
}

/** One account: its name and balance (tap to open), then the month's recurring bills still to pay. */
@Composable
private fun AccountCard(model: AppModel, account: String, summary: AccountSummary?, avatar: Pair<Color, Color>, hidden: Boolean) {
    val s = LocalStrings.current
    val type = LocalReinoType.current
    val sep = LocalDecimalSeparator.current
    val symbol = summary?.currency?.symbol.orEmpty()
    val title = summary?.title ?: account
    fun money(v: Double) = if (hidden) MASK else formatMoney(v, sep, symbol)
    val numbers = TextStyle(fontFeatureSettings = "tnum")

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(D.card).border(1.dp, D.cardBorder, RoundedCornerShape(14.dp))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable { model.openAccount(account) }
                .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(avatar.first), contentAlignment = Alignment.Center) {
                Text(
                    title.take(1).uppercase(),
                    style = TextStyle(fontFamily = type.serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                    color = avatar.second,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), fontWeight = FontWeight.SemiBold, color = D.ink, maxLines = 1)
                if (summary != null) {
                    Text(
                        if (summary.balance == null) s.noOpeningBalanceYet else s.balance,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = if (summary.balance == null) D.todoInk else D.soft,
                    )
                }
            }
            summary?.balance?.let {
                Text(money(it), style = numbers.merge(MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)), fontWeight = FontWeight.Medium, color = if (it < 0) D.red else D.ink)
            }
            LineIcon(Icons24.ChevronRight, Color(0xFF8A847A), size = 18.dp, strokeWidth = 2f)
        }
        if (summary == null) return@Column

        Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
        if (summary.pending.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(D.field).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LineIcon(Icons24.Check, D.tagIncomeInk, size = 16.dp, strokeWidth = 2.2f)
                Text("${s.pendingBills} · 0", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = D.tagIncomeInk, modifier = Modifier.weight(1f))
                Text(money(0.0), style = numbers.merge(MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)), color = D.tagIncomeInk)
            }
            return@Column
        }

        var open by remember(account) { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().background(D.field).clickable { open = !open }.heightIn(min = 52.dp)
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(D.todoBorder))
            Text(
                "${s.pendingBills} · ${summary.pending.size}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6B4A10),
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(money(summary.pendingTotal), style = numbers.merge(MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)), fontWeight = FontWeight.Medium, color = D.ink)
                if (summary.awaitingAmount > 0) {
                    Text(s.plusVariable(summary.awaitingAmount), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = D.soft)
                }
            }
            LineIcon(if (open) Icons24.ChevronUp else Icons24.ChevronDown, D.soft, size = 18.dp, strokeWidth = 2f)
        }
        if (open) {
            summary.pending.forEach { bill ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                BillLine(model, bill, summary, hidden, ::money)
            }
        }
    }
}

/**
 * One unpaid recurring bill. A fixed bill, or a variable one with its amount in, is checked off to
 * pay it; a variable bill still without an amount asks for it first, and can't be paid until then.
 */
@Composable
private fun BillLine(model: AppModel, bill: Expense, summary: AccountSummary, hidden: Boolean, money: (Double) -> String) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val amount = bill.amount(summary.month)
    val needsAmount = bill.type == ExpenseType.RECURRING_VARIABLE && amount == null

    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReinoCheckbox(
            checked = false,
            onToggle = if (needsAmount) null else ({ model.markPaidThisMonth(bill) }),
            accent = D.teal,
            modifier = if (needsAmount) Modifier.alpha(0.4f) else Modifier,
        )
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(bill.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = D.ink)
            if (needsAmount) Text(s.variableEnterAmount, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = D.todoInk)
        }
        if (needsAmount) {
            var text by remember(bill.id, sep) { mutableStateOf("") }
            Row(
                Modifier.padding(end = 12.dp).width(104.dp).height(40.dp).clip(RoundedCornerShape(8.dp)).background(D.card)
                    .dashedBox(D.todoBorder, 8).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(summary.currency.symbol, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = D.soft)
                ReinoField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontSize = 15.sp, fontFeatureSettings = "tnum", textAlign = TextAlign.End, color = D.ink),
                    placeholder = "0${sep}00",
                    contentAlignment = Alignment.CenterEnd,
                    keyboardType = KeyboardType.Decimal,
                    onFocusLost = { parseAmount(text)?.let { model.setVariableAmountFromList(bill, it) } },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(amount?.let { money(it) } ?: "—", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"), color = D.ink, modifier = Modifier.padding(end = 12.dp))
        }
    }
}
