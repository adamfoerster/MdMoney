package com.mdmoney.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Reino Eterno palette: a warm archival scheme — aged paper, warm near-black ink, and a single
 * bronze/ochre accent. No pure white, no pure black. The brand is light-only, so there is no dark
 * variant; depth is drawn with hairline rules rather than shadow.
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
    paper = Color(0xFFF3EAD9),
    paperDeep = Color(0xFFEDE2CD),
    paperEdge = Color(0xFFE6DABF),
    ink = Color(0xFF241E16),
    inkSoft = Color(0xFF5B5142),
    inkFaint = Color(0xFF8A7F6D),
    brass = Color(0xFF8B6B38),
    brassDeep = Color(0xFF6F5429),
    brassBand = Color(0xFF927540),
    brassTint = Color(0x1A8B6B38),
    verdigris = Color(0xFF3F6B54),
    verdigrisTint = Color(0x1A3F6B54),
    line = Color(0x29241E16),
    lineStrong = Color(0x52241E16),
    lineOnBrass = Color(0x47F3EAD9),
    onBrass = Color(0xFFF3EAD9),
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
    error = Color(0xFF8B3A2E),
    onError = onBrass,
)
