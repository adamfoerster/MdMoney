package com.mdmoney.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mdmoney.resources.Res
import com.mdmoney.resources.hanken_grotesk_bold
import com.mdmoney.resources.hanken_grotesk_medium
import com.mdmoney.resources.hanken_grotesk_regular
import com.mdmoney.resources.hanken_grotesk_semibold
import com.mdmoney.resources.ibm_plex_mono_medium
import com.mdmoney.resources.ibm_plex_mono_regular
import com.mdmoney.resources.ibm_plex_mono_semibold
import com.mdmoney.resources.spectral_medium
import com.mdmoney.resources.spectral_regular
import com.mdmoney.resources.spectral_semibold
import org.jetbrains.compose.resources.Font

/** The three brand typefaces: Spectral (serif), Hanken Grotesk (sans), IBM Plex Mono (labels). */
data class ReinoTypefaces(val serif: FontFamily, val sans: FontFamily, val mono: FontFamily)

val LocalReinoType = staticCompositionLocalOf<ReinoTypefaces> {
    error("ReinoTypefaces not provided")
}

/** Mono labels are always tracked out (letter-spaced) and, at call sites, uppercased. */
val LabelTracking = 0.14.em

@Composable
fun rememberReinoTypefaces(): ReinoTypefaces {
    val serif = FontFamily(
        Font(Res.font.spectral_regular, FontWeight.Normal),
        Font(Res.font.spectral_medium, FontWeight.Medium),
        Font(Res.font.spectral_semibold, FontWeight.SemiBold),
    )
    val sans = FontFamily(
        Font(Res.font.hanken_grotesk_regular, FontWeight.Normal),
        Font(Res.font.hanken_grotesk_medium, FontWeight.Medium),
        Font(Res.font.hanken_grotesk_semibold, FontWeight.SemiBold),
        Font(Res.font.hanken_grotesk_bold, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_mono_medium, FontWeight.Medium),
        Font(Res.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    )
    return ReinoTypefaces(serif, sans, mono)
}

fun reinoTypography(t: ReinoTypefaces): Typography = Typography(
    // Serif — display & headings.
    displayLarge = TextStyle(fontFamily = t.serif, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 42.sp),
    displayMedium = TextStyle(fontFamily = t.serif, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = t.serif, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = t.serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = t.serif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 24.sp),
    // Sans — body & descriptions.
    bodyLarge = TextStyle(fontFamily = t.sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = t.sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = t.sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    // Mono — labels, numbers, tags, nav (uppercased + tracked at call sites).
    labelLarge = TextStyle(fontFamily = t.mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = LabelTracking),
    labelMedium = TextStyle(fontFamily = t.mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = LabelTracking),
    labelSmall = TextStyle(fontFamily = t.mono, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = LabelTracking),
)
