package com.mdmoney.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mdmoney.ui.theme.LocalReinoColors

/** The mark is 16dp because the brand says so; see [ReinoCheckbox] for why the target is not. */
private val CheckMark = 16.dp
private val CheckTarget = 40.dp

/**
 * The brand's checkbox: a square hairline that fills brass with a ✓, as the design system's
 * `TaskList` draws it. Flat and instant — no ripple, no elevation.
 *
 * A null [onToggle] makes it a statement of fact rather than a control, and — unlike Material's
 * checkbox, which silently drops its touch target when it has no callback — it keeps occupying the
 * same width either way. That difference is what left read-only rows sitting a few pixels off from
 * the rest of the list.
 */
@Composable
fun ReinoCheckbox(
    checked: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    accent: Color = LocalReinoColors.current.brass,
) {
    val reino = LocalReinoColors.current
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            // 16dp is the mark, not the target: a finger is not 16dp wide, so the hit area is the
            // one thing here that doesn't take its size from the design system.
            .size(CheckTarget)
            .then(
                if (onToggle == null) Modifier
                else Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(CheckMark)
                .background(if (checked) accent else Color.Transparent, shape)
                .border(1.dp, if (checked) accent else reino.lineStrong, shape)
                .drawBehind {
                    if (!checked) return@drawBehind
                    // Drawn rather than typeset: the brand's mono has no ✓, and the glyph a font
                    // falls back to is a stray letter shape at 11sp.
                    val w = size.width
                    val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Square)
                    val path = Path().apply {
                        moveTo(w * 0.24f, w * 0.52f)
                        lineTo(w * 0.42f, w * 0.70f)
                        lineTo(w * 0.76f, w * 0.32f)
                    }
                    drawPath(path, color = reino.onBrass, style = stroke)
                },
        )
    }
}

/** Field padding. Matched by [reinoFieldInset] so a read-only value lines up with an editable one. */
private val FieldPadH = 4.dp
private val FieldPadV = 2.dp
private val Dash = 3.dp

/**
 * Pads a read-only value exactly as [ReinoField] pads an editable one.
 *
 * The design system cancels the input's padding with a negative margin so that typed and printed
 * values sit on the same line. Compose has no negative margin, so the same result comes from giving
 * the printed value the padding too — without which a column of numbers wanders by a few pixels
 * depending on whether the row happens to be editable.
 */
fun Modifier.reinoFieldInset(): Modifier = padding(horizontal = FieldPadH, vertical = FieldPadV)

/**
 * The brand's editable field: no box, a dashed rule beneath, brass-tinted on hover, and on focus a
 * deeper paper with the rule gone solid brass.
 *
 * The dash carries meaning rather than decoration — the design system prints a legend for it
 * ("— campo editável"), so a dashed underline is the app telling you this value can be typed over,
 * and a value with no dash is one that can't. A boxed field would say it twice and louder.
 *
 * [onFocusLost] fires when the field gives up focus, which is where a caller commits: the user is
 * mid-thought while typing, and writing a file on every keystroke would be both.
 */
@Composable
fun ReinoField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: String? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    keyboardType: KeyboardType = KeyboardType.Text,
    onFocusLost: () -> Unit = {},
    /** A faint currency symbol shown just left of a typed value (hidden while the field is empty). */
    prefix: String? = null,
) {
    val reino = LocalReinoColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(reino.brass),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .hoverable(interaction)
            .onFocusChanged { state ->
                if (focused && !state.isFocused) onFocusLost()
                focused = state.isFocused
            }
            .background(
                when {
                    focused -> reino.paperDeep
                    hovered -> reino.brassTint
                    else -> Color.Transparent
                },
                RoundedCornerShape(2.dp),
            )
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(
                    color = if (focused) reino.brass else reino.lineStrong,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    // Solid once focused: the field has stopped offering and started listening.
                    pathEffect = if (focused) null else {
                        PathEffect.dashPathEffect(floatArrayOf(Dash.toPx(), Dash.toPx()))
                    },
                )
            }
            .reinoFieldInset(),
        // Aligning the *box* rather than trusting textStyle.textAlign: the inner field sizes to its
        // own text, so an end-aligned style would still leave the value floating short of the
        // column's edge — which is exactly how a money column stops being a column.
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = contentAlignment) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = textStyle, color = reino.inkFaint)
                }
                // Symbol and figure travel together as one right-aligned group; the symbol is left
                // off while empty so the placeholder dash stands alone.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!prefix.isNullOrEmpty() && value.isNotEmpty()) {
                        Text("$prefix ", style = textStyle, color = reino.inkFaint)
                    }
                    inner()
                }
            }
        },
    )
}
