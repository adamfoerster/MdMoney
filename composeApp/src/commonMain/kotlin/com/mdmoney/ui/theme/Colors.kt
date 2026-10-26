package com.mdmoney.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Aldermoor "bank" theme: ivory grounds, midnight-navy primary and ink, hairline rules. Field
 * names are kept from the earlier palette: [brass*] now carry the navy primary, [verdigris*] the
 * success teal used for income. Light-only; depth is drawn with hairline rules rather than shadow.
 */
data class ReinoColors(
    val paper: Color,
    val paperDeep: Color,
    val paperEdge: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val brass: Color,
    val brassDeep: Color,
    val brassBand: Color,
    val brassTint: Color,
    /** Aged-copper patina: the one counter-accent, reserved for income (money in). */
    val verdigris: Color,
    val verdigrisTint: Color,
    val line: Color,
    val lineStrong: Color,
    val lineOnBrass: Color,
    val onBrass: Color,
)

val ReinoPalette = ReinoColors(
    paper = Color(0xFFF7F2E8),
    paperDeep = Color(0xFFEFE8DA),
    paperEdge = Color(0xFFE4DAC6),
    ink = Color(0xFF161B26),
    inkSoft = Color(0xFF5A5F6B),
    inkFaint = Color(0xFF868B96),
    brass = Color(0xFF111A2B),
    brassDeep = Color(0xFF1B2840),
    brassBand = Color(0xFF111A2B),
    brassTint = Color(0xFFE1E4EA),
    verdigris = Color(0xFF1F5659),
    verdigrisTint = Color(0xFFDDEBEA),
    line = Color(0xFFD9D6CF),
    lineStrong = Color(0xFF868B96),
    lineOnBrass = Color(0x47FCFAF5),
    onBrass = Color(0xFFFCFAF5),
)

val LocalReinoColors = staticCompositionLocalOf { ReinoPalette }

/** Maps the palette onto Material 3 slots so stock components inherit the brand. */
fun ReinoColors.toMaterialColorScheme(): ColorScheme = lightColorScheme(
    primary = brass,
    onPrimary = onBrass,
    primaryContainer = brassTint,
    onPrimaryContainer = brassDeep,
    secondary = inkSoft,
    onSecondary = onBrass,
    background = paper,
    onBackground = ink,
    surface = paper,
    onSurface = ink,
    surfaceVariant = paperDeep,
    onSurfaceVariant = inkSoft,
    surfaceContainerLowest = paper,
    surfaceContainerLow = paper,
    surfaceContainer = paperDeep,
    surfaceContainerHigh = paperDeep,
    surfaceContainerHighest = paperEdge,
    outline = lineStrong,
    outlineVariant = line,
    error = Color(0xFFA02D22),
    onError = onBrass,
)
