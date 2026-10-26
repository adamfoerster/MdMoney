package com.mdmoney.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalStrings
import com.mdmoney.ui.HomeTab
import com.mdmoney.ui.theme.D

/**
 * The bottom navigation: line icon over a small sentence-case label on a card-coloured bar with a
 * hairline on top. The current tab reads in ink and semibold, the rest in the muted ink. [enabled]
 * dims the tabs that need an open account.
 */
@Composable
fun ReinoTabBar(
    current: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (HomeTab) -> Boolean = { true },
) {
    val s = LocalStrings.current
    val items = listOf(
        Triple(HomeTab.HOME, s.home, Icons24.Home),
        Triple(HomeTab.ANNUAL, s.annual, Icons24.Annual),
        Triple(HomeTab.REPORTS, s.reports, Icons24.Reports),
        Triple(HomeTab.SETTINGS, s.settings, Icons24.Settings),
    )
    Column(modifier.fillMaxWidth().background(D.card)) {
        Row(Modifier.fillMaxWidth().height(1.dp).background(D.cardBorder)) {}
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            items.forEach { (tab, label, icon) ->
                val selected = tab == current
                val on = enabled(tab)
                val color = (if (selected) D.ink else D.raw).let { if (on) it else it.copy(alpha = 0.4f) }
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp).clickable(enabled = on) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    LineIcon(icon, color)
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
