package com.twelvepts.cathode.ui

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.sin
import kotlin.math.cos

@Composable
fun ArtworkBackdrop(artworkUri: Uri?, animations: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(CathodeBlack)) {
        if (artworkUri == null) {
            CathodeBackdrop(animations)
        } else {
            Crossfade(artworkUri, animationSpec = tween(if (animations) 700 else 0), label = "global-artwork") { artwork ->
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(24.dp).alpha(.46f),
                )
            }
            SignalDust(animations)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to CathodeBlack.copy(alpha = .36f),
                        .58f to CathodeBlack.copy(alpha = .57f),
                        1f to CathodeBlack.copy(alpha = .86f),
                    ),
                ),
            )
        }
    }
}

@Composable
fun SignalDust(animations: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "signal-dust")
    val phase by transition.animateFloat(
        0f, 6.2831855f,
        infiniteRepeatable(tween(if (animations) 42_000 else 1_000_000), RepeatMode.Restart),
        label = "dust-phase",
    )
    Canvas(modifier.fillMaxSize()) {
        repeat(64) { index ->
            val seed = index * 47.17f
            val margin = 24f
            val spanX = (size.width - margin * 2f).coerceAtLeast(1f)
            val spanY = (size.height - margin * 2f).coerceAtLeast(1f)
            val baseX = margin + (seed * 29f) % spanX
            val baseY = margin + (seed * 13f) % spanY
            val xPhase = phase * (1 + index % 3) + index * .71f
            val yPhase = phase * (1 + index % 4) + index * 1.13f
            val x = (baseX + sin(xPhase) * (10f + index % 5 * 4f)).coerceIn(0f, size.width)
            val y = (baseY + cos(yPhase) * (16f + index % 6 * 5f)).coerceIn(0f, size.height)
            val shimmerPhase = phase * (1 + index % 2) + index * .89f
            val shimmer = .42f + .58f * ((sin(shimmerPhase) + 1f) / 2f)
            drawCircle(
                color = if (index % 5 == 0) CathodeCyan.copy(alpha = .30f * shimmer) else Color.White.copy(alpha = .14f * shimmer),
                radius = 7.5f + index % 8,
                center = Offset(x, y),
            )
        }
    }
}

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
