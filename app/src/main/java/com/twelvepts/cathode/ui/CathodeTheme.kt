package com.twelvepts.cathode.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

val CathodeBlack = Color(0xFF03090B)
val CathodePanel = Color(0xFF071216)
val CathodeCyan = Color(0xFF00E5FF)
val CathodeBright = Color(0xFF67F3FF)
val CathodeDim = Color(0xFF147D89)
val CathodeText = Color(0xFFE5FCFF)
val CathodeMuted = Color(0xFF74959A)
val CathodeError = Color(0xFFFF5263)

private val Colors = darkColorScheme(
    primary = CathodeCyan,
    onPrimary = CathodeBlack,
    secondary = CathodeBright,
    background = CathodeBlack,
    onBackground = CathodeText,
    surface = CathodePanel,
    onSurface = CathodeText,
    surfaceVariant = Color(0xFF0B1B20),
    onSurfaceVariant = CathodeMuted,
    outline = CathodeDim,
    error = CathodeError,
)

private val CathodeTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 34.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
)

@Composable
fun CathodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = CathodeTypography, content = content)
}
