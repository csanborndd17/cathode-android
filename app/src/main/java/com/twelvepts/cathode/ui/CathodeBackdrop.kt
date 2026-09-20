package com.twelvepts.cathode.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun CathodeBackdrop(animations: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "phosphor-field")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (animations) 12_000 else 1_000_000), RepeatMode.Reverse),
        label = "phosphor-drift",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(CathodeBlack)
        val center = Offset(size.width * (.2f + drift * .6f), size.height * .18f)
        drawRect(
            brush = Brush.radialGradient(
                listOf(CathodeCyan.copy(alpha = .12f + CathodeGlowStrength * .06f), Color.Transparent),
                center = center,
                radius = size.maxDimension * .72f,
            ),
        )
        val lineColor = CathodeCyan.copy(alpha = .018f + CathodeGlowStrength * .018f)
        var y = 0f
        while (y < size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f)
            y += 7f
        }
        val gridColor = CathodeDim.copy(alpha = .025f)
        var x = 0f
        while (x < size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            x += 72f
        }
    }
}
