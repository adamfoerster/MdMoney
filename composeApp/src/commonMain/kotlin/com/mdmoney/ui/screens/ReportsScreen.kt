package com.mdmoney.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalStrings
import com.mdmoney.ui.components.Eyebrow
import com.mdmoney.ui.theme.LocalReinoColors

/** Placeholder for the future reports tab. */
@Composable
fun ReportsScreen() {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(28.dp))
            Eyebrow("${s.appName} — ${s.reports}")
            Text(
                s.reports,
                style = MaterialTheme.typography.displayMedium,
                color = reino.ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(s.reportsComingSoon, style = MaterialTheme.typography.bodyLarge, color = reino.inkSoft)
        }
    }
}
