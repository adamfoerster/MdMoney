package com.mdmoney.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A stroked icon drawn from SVG path data on the usual 24-unit grid, so it takes the text colour. */
@Composable
fun LineIcon(paths: List<String>, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, strokeWidth: Float = 1.8f) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val k = this.size.width / 24f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            parsed.forEach { drawPath(it, color, style = stroke) }
        }
    }
}

object Icons24 {
    val Home = listOf("M3 11l9-7 9 7", "M5 10v10h14V10")
    val Annual = listOf("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z", "M3 10h18M8 3v4M16 3v4")
    val Reports = listOf("M5 20V10M12 20V4M19 20v-7")
    val Settings = listOf(
        "M15 12a3 3 0 1 1 -6 0 3 3 0 0 1 6 0z",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1 -2.8 2.8l-.1-.1a1.7 1.7 0 0 0 -1.8 -.3 1.7 1.7 0 0 0 -1 1.5V21a2 2 0 1 1 -4 0v-.1a1.7 1.7 0 0 0 -1.1 -1.5 1.7 1.7 0 0 0 -1.8 .3l-.1.1a2 2 0 1 1 -2.8 -2.8l.1-.1a1.7 1.7 0 0 0 .3 -1.8 1.7 1.7 0 0 0 -1.5 -1H3a2 2 0 1 1 0 -4h.1a1.7 1.7 0 0 0 1.5 -1.1 1.7 1.7 0 0 0 -.3 -1.8l-.1-.1a2 2 0 1 1 2.8 -2.8l.1.1a1.7 1.7 0 0 0 1.8 .3H9a1.7 1.7 0 0 0 1 -1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8 -.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0 -.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0 -1.5 1z",
    )
    val Eye = listOf("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", "M15 12a3 3 0 1 1 -6 0 3 3 0 0 1 6 0z")
    val EyeOff = Eye + "M4 4l16 16"
    val ChevronRight = listOf("M9 6l6 6-6 6")
    val ChevronDown = listOf("M6 9l6 6 6-6")
    val ChevronUp = listOf("M6 15l6-6 6 6")
    val ChevronLeft = listOf("M15 18l-6-6 6-6")
    val Close = listOf("M6 6l12 12M18 6L6 18")
    val Search = listOf("M18 11a7 7 0 1 1 -14 0 7 7 0 0 1 14 0z", "M20 20l-4-4")
    val Info = listOf("M21 12a9 9 0 1 1 -18 0 9 9 0 0 1 18 0z", "M12 11v5M12 8h.01")
    val Plus = listOf("M12 5v14M5 12h14")
    val Check = listOf("M5 12l5 5 9-10")
    val Pencil = listOf("M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val Trash = listOf("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val MoveRight = listOf("M4 12h13M13 7l5 5-5 5", "M20 4v16")
    val Dots = listOf("M5 12h.01M12 12h.01M19 12h.01")
}
