package com.mdmoney.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalStrings
import com.mdmoney.ui.HomeTab
import com.mdmoney.ui.theme.LocalReinoColors

/**
 * The brand's bottom navigation: a hairline-topped row of mono, uppercase, tracked labels. Selected
 * tab reads in brass under a short brass indicator; the rest sit faint. Flat — no elevation.
 */
@Composable
fun ReinoTabBar(current: HomeTab, onSelect: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    val reino = LocalReinoColors.current
    val s = LocalStrings.current
    val items = listOf(
        HomeTab.HOME to s.home,
        HomeTab.ANNUAL to s.annual,
        HomeTab.REPORTS to s.reports,
        HomeTab.SETTINGS to s.settings,
    )
    Column(modifier.fillMaxWidth().background(reino.paper)) {
        HairlineDivider(strong = true)
        Row(Modifier.fillMaxWidth().height(58.dp)) {
            items.forEach { (tab, label) ->
                val selected = tab == current
                Column(
                    Modifier.weight(1f).fillMaxWidth().clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.height(2.dp).width(28.dp)
                            .background(if (selected) reino.brass else Color.Transparent),
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) reino.brass else reino.inkFaint,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
