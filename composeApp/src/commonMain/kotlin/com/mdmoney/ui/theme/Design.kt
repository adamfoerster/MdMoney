package com.mdmoney.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colours of the mobile redesign (import, account, group), shared by the screens that use it. */
object D {
    val bg = Color(0xFFF5F0E6)
    val card = Color(0xFFFFFDF8)
    val cardBorder = Color(0xFFDDD5C6)
    val rule = Color(0xFFE4DCCD)
    val ink = Color(0xFF1C1B19)
    val soft = Color(0xFF5F5A50)
    val raw = Color(0xFF6B665C)
    val dark = Color(0xFF1C1B19)
    val onDark = Color(0xFFFBF8F1)
    val dim = Color(0xFFCFC8BA)
    val teal = Color(0xFF2F5D57)
    val red = Color(0xFF9A3B1E)
    val field = Color(0xFFFBF8F1)
    val fieldBorder = Color(0xFFD6CDBC)
    val todo = Color(0xFFFBF1DD)
    val todoBorder = Color(0xFFC9A15A)
    val todoInk = Color(0xFF8A5A12)
    val accountBg = Color(0xFFECE6F7)
    val accountBorder = Color(0xFFCFC4E8)
    val noteBg = Color(0xFFF6E9CF)
    val noteBorder = Color(0xFFE5CC9A)
    val noteInk = Color(0xFF4A3510)
    val bar = Color(0xFFEFE8DA)
    val tag = Color(0xFFECE6DA)
    val tagInk = Color(0xFF4A463E)
    val tagIncome = Color(0xFFDCEAE5)
    val tagIncomeInk = Color(0xFF23473F)
    val accent = Color(0xFF463A6B)
    val accentSoft = Color(0xFFF3EFFA)
}

fun Modifier.box(bg: Color, border: Color, radius: Int): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return clip(shape).background(bg).border(1.dp, border, shape)
}

fun Modifier.importCard(): Modifier = box(D.card, D.cardBorder, 12)
