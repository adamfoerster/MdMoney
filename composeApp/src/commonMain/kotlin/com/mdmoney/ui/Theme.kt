package com.mdmoney.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.mdmoney.ui.theme.LocalReinoColors
import com.mdmoney.ui.theme.LocalReinoType
import com.mdmoney.ui.theme.ReinoPalette
import com.mdmoney.ui.theme.reinoTypography
import com.mdmoney.ui.theme.rememberReinoTypefaces
import com.mdmoney.ui.theme.toMaterialColorScheme

// Aldermoor is architectural, nearly square: 3px controls, 4px panels.
private val ReinoShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

/**
 * Aldermoor theme (bank). Light-only here (ivory); ignores the system dark setting because
 * the brand has no dark variant. Provides both the Material 3 mapping and the extended [ReinoColors]
 * and typefaces to the tree.
 */
@Composable
fun MdMoneyTheme(content: @Composable () -> Unit) {
    val typefaces = rememberReinoTypefaces()
    CompositionLocalProvider(
        LocalReinoColors provides ReinoPalette,
        LocalReinoType provides typefaces,
    ) {
        MaterialTheme(
            colorScheme = ReinoPalette.toMaterialColorScheme(),
            typography = reinoTypography(typefaces),
            shapes = ReinoShapes,
            content = content,
        )
    }
}
