package com.mdmoney.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdmoney.LocalStrings
import com.mdmoney.domain.Category
import com.mdmoney.domain.resolveCategorySlug
import com.mdmoney.ui.components.Icons24
import com.mdmoney.ui.components.LineIcon
import com.mdmoney.ui.filterCategories
import com.mdmoney.ui.theme.D
import com.mdmoney.ui.theme.LocalReinoType

private val Plum = Color(0xFF463A6B)
private val Field = Color(0xFFC9C0AF)

/** The category page: search-or-create, the account's most used categories, then all of them. */
@Composable
internal fun CategoryPickerPage(
    categories: List<Category>,
    mostUsed: List<String>,
    selected: String?,
    onPick: (String?) -> Unit,
    onBack: () -> Unit,
    /** When set, a first row with this label picks "no category" (null). */
    noneLabel: String? = null,
) {
    val s = LocalStrings.current
    var query by remember { mutableStateOf("") }
    val shown = filterCategories(categories, query)
    val typed = resolveCategorySlug(query, categories)
    val exists = categories.any { it.slug == typed || it.title.equals(query.trim(), ignoreCase = true) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                LineIcon(Icons24.ChevronLeft, D.ink, size = 22.dp, strokeWidth = 2f)
            }
            Text(
                s.chooseCategory,
                style = TextStyle(fontFamily = LocalReinoType.current.serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                color = D.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(44.dp))
        }
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp))
                .background(D.card).border(1.dp, Field, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LineIcon(Icons24.Search, D.raw, size = 18.dp, strokeWidth = 2f)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = D.ink),
                    cursorBrush = SolidColor(D.ink),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) Text(s.searchOrCreate, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = D.raw)
            }
        }

        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val recent = mostUsed.mapNotNull { slug -> categories.firstOrNull { it.slug == slug } }
            if (recent.isNotEmpty() && query.isBlank()) {
                PickerHeading(s.mostUsed)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { c ->
                        val on = c.slug == selected
                        Row(
                            Modifier.height(40.dp).clip(RoundedCornerShape(999.dp)).background(if (on) D.dark else D.card)
                                .border(1.dp, if (on) D.dark else Color(0xFFD6CDBC), RoundedCornerShape(999.dp))
                                .clickable { onPick(c.slug) }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (on) LineIcon(Icons24.Check, D.onDark, size = 14.dp, strokeWidth = 2.4f)
                            Text(c.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = if (on) D.onDark else D.ink)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { PickerHeading(s.allCategories) }
                Text(shown.size.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = D.soft, modifier = Modifier.padding(top = 14.dp, end = 4.dp))
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D.card).border(1.dp, D.cardBorder, RoundedCornerShape(12.dp))) {
                val rows = buildList<Pair<String?, String>> {
                    if (noneLabel != null && query.isBlank()) add(null to noneLabel)
                    if (query.isNotBlank() && !exists && typed != null) add(typed to s.createCategory(query.trim()))
                    shown.forEach { add(it.slug to it.title) }
                }
                rows.forEachIndexed { i, (slug, label) ->
                    val on = slug == selected
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(D.rule))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(if (on) D.accountBg else Color.Transparent)
                            .clickable { onPick(slug) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = D.ink, modifier = Modifier.weight(1f))
                        if (on) LineIcon(Icons24.Check, Plum, size = 18.dp, strokeWidth = 2.4f)
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(D.card)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(D.cardBorder))
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp).fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(10.dp)).dashedBox(Color(0xFF8A847A), 10)
                    .clickable(enabled = typed != null) { typed?.let { onPick(it) } },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                LineIcon(Icons24.Plus, D.ink, size = 18.dp, strokeWidth = 2f)
                Text(s.newCategory, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontWeight = FontWeight.Medium, color = D.ink)
            }
        }
    }
}

@Composable
private fun PickerHeading(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = D.soft, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
}

