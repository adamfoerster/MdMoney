package com.mdmoney.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mdmoney.ui.theme.LocalReinoColors

/** Mono, uppercase, tracked-out label — the brand's signature eyebrow/kicker. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = LocalReinoColors.current.brass) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier,
    )
}

/** A hairline rule — the brand draws depth with rules, not shadows. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, strong: Boolean = false) {
    val reino = LocalReinoColors.current
    Row(modifier.fillMaxWidth().height(1.dp).background(if (strong) reino.lineStrong else reino.line)) {}
}

/**
 * Brass section header: serif title on the left, mono meta (`08  /  STUDY NOTES`) on the right.
 */
@Composable
fun SectionBand(title: String, number: String? = null, label: String? = null, modifier: Modifier = Modifier) {
    val reino = LocalReinoColors.current
    Row(
        modifier.fillMaxWidth().background(reino.brassBand).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = reino.onBrass)
        if (number != null || label != null) {
            val meta = listOfNotNull(number, label?.uppercase()).joinToString("  /  ")
            Text(meta, style = MaterialTheme.typography.labelMedium, color = reino.onBrass.copy(alpha = 0.92f))
        }
    }
}

/**
 * A numbered archive index row: mono number in brass, serif title, sans description, and optional
 * mono meta — separated from its neighbours by a top hairline.
 */
@Composable
fun IndexRow(
    number: String,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    meta: (@Composable RowScopeMeta.() -> Unit)? = null,
) {
    val reino = LocalReinoColors.current
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        HairlineDivider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelLarge,
                color = reino.brass,
                modifier = Modifier.padding(top = 6.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = reino.ink)
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = reino.inkSoft,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (meta != null) {
                    Row(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { RowScopeMeta.meta() }
                }
            }
        }
    }
}

/** Marker receiver so index-row meta content reads as brand meta chips. */
object RowScopeMeta

/** Mono meta chip: brass "term" + faint uppercase count, as on the index rows. */
@Composable
fun RowScopeMeta.Meta(term: String, count: String? = null) {
    val reino = LocalReinoColors.current
    Text(term, style = MaterialTheme.typography.labelMedium, color = reino.brass)
    if (count != null) {
        Text(count.uppercase(), style = MaterialTheme.typography.labelSmall, color = reino.inkFaint)
    }
}

enum class ReinoButtonVariant { Primary, Secondary, Ghost }

/** Mono, uppercase, square (2px) button in the three brand variants. Flat — no elevation. */
@Composable
fun ReinoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ReinoButtonVariant = ReinoButtonVariant.Primary,
    enabled: Boolean = true,
    trailingArrow: Boolean = false,
) {
    val reino = LocalReinoColors.current
    val shape = RoundedCornerShape(2.dp)
    val bg: Color
    val fg: Color
    val border: Color
    when (variant) {
        ReinoButtonVariant.Primary -> { bg = reino.brass; fg = reino.onBrass; border = reino.brass }
        ReinoButtonVariant.Secondary -> { bg = Color.Transparent; fg = reino.ink; border = reino.lineStrong }
        ReinoButtonVariant.Ghost -> { bg = Color.Transparent; fg = reino.brass; border = Color.Transparent }
    }
    val hPad = if (variant == ReinoButtonVariant.Ghost) 0.dp else 22.dp
    Row(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (border != Color.Transparent) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = hPad, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = fg)
        if (trailingArrow) Text("→", style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
