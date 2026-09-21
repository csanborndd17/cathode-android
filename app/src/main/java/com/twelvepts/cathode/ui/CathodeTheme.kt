package com.twelvepts.cathode.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

var CathodeBlack = Color(0xFF03090B)
var CathodePanel = Color(0xFF071216)
var CathodeCyan = Color(0xFF00E5FF)
var CathodeBright = Color(0xFF67F3FF)
var CathodeDim = Color(0xFF147D89)
var CathodeText = Color(0xFFE5FCFF)
var CathodeMuted = Color(0xFF74959A)
val CathodeError = Color(0xFFFF5263)
var CathodeGlowStrength = .35f

@Composable
fun CathodeTheme(settings: CathodeSettings, content: @Composable () -> Unit) {
    val presetAccent = when (settings.themePreset) {
        ThemePreset.CYAN -> Color(0xFF00E5FF)
        ThemePreset.AMBER -> Color(0xFFFFB300)
        ThemePreset.GREEN -> Color(0xFF4CFF8A)
        ThemePreset.ULTRAVIOLET -> Color(0xFFB388FF)
        ThemePreset.ICE -> Color(0xFFE4F7FF)
    }
    val accent = settings.customAccentArgb?.let(::Color) ?: presetAccent
    CathodeBlack = if (settings.amoled) Color.Black else Color(0xFF03090B)
    CathodePanel = (if (settings.amoled) Color(0xFF050505) else lerp(CathodeBlack, accent, .055f)).copy(alpha = .88f)
    CathodeCyan = accent
    CathodeBright = lerp(accent, Color.White, .35f)
    CathodeDim = lerp(accent, CathodeBlack, .48f)
    CathodeText = lerp(Color.White, accent, .08f)
    CathodeMuted = lerp(Color(0xFF7D8588), accent, .18f)
    CathodeGlowStrength = settings.glowStrength

    val family = if (settings.monospace) FontFamily.Monospace else FontFamily.SansSerif
    val adjustment = if (settings.compact) -1 else 0
    val typography = Typography(
        displaySmall = TextStyle(fontFamily = family, fontSize = (34 + adjustment).sp),
        headlineMedium = TextStyle(fontFamily = family, fontSize = (28 + adjustment).sp),
        titleLarge = TextStyle(fontFamily = family, fontSize = (22 + adjustment).sp),
        titleMedium = TextStyle(fontFamily = family, fontSize = (17 + adjustment).sp),
        bodyLarge = TextStyle(fontFamily = family, fontSize = (16 + adjustment).sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = (14 + adjustment).sp),
        labelLarge = TextStyle(fontFamily = if (settings.monospace) family else FontFamily.Monospace, fontSize = (13 + adjustment).sp),
        labelMedium = TextStyle(fontFamily = family, fontSize = (12 + adjustment).sp),
    )
    val radius = if (settings.rounded) 18.dp else 2.dp
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(radius / 2),
        small = RoundedCornerShape(radius / 2),
        medium = RoundedCornerShape(radius),
        large = RoundedCornerShape(radius),
        extraLarge = RoundedCornerShape(radius),
    )
    val colors = darkColorScheme(
        primary = CathodeCyan,
        onPrimary = CathodeBlack,
        secondary = CathodeBright,
        onSecondary = CathodeBlack,
        primaryContainer = CathodePanel,
        onPrimaryContainer = CathodeText,
        secondaryContainer = lerp(CathodePanel, CathodeCyan, .10f),
        onSecondaryContainer = CathodeText,
        tertiaryContainer = CathodePanel,
        onTertiaryContainer = CathodeText,
        background = CathodeBlack,
        onBackground = CathodeText,
        surface = CathodePanel,
        onSurface = CathodeText,
        surfaceVariant = lerp(CathodePanel, CathodeCyan, .08f),
        onSurfaceVariant = CathodeMuted,
        outline = CathodeDim,
        error = CathodeError,
    )
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
        CompositionLocalProvider(LocalContentColor provides CathodeText) {
            content()
        }
    }
}
