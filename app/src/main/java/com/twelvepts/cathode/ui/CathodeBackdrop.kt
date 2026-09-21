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
        0f, 1f,
        infiniteRepeatable(tween(if (animations) 18_000 else 1_000_000), RepeatMode.Restart),
        label = "dust-phase",
    )
    Canvas(modifier.fillMaxSize()) {
        repeat(64) { index ->
            val seed = index * 47.17f
            val x = ((seed * 29f) % size.width + phase * size.width * (.04f + index % 4 * .012f)) % size.width
            val baseY = (seed * 13f) % size.height
            val y = (baseY - phase * size.height * (.08f + index % 5 * .015f) + size.height) % size.height
            val shimmer = .35f + .65f * ((sin(phase * 6.28f + index) + 1f) / 2f)
            drawCircle(
                color = if (index % 5 == 0) CathodeCyan.copy(alpha = .30f * shimmer) else Color.White.copy(alpha = .14f * shimmer),
                radius = 1.8f + index % 4,
                center = Offset(x, y),
            )
        }
        val streakY = size.height * (.18f + phase * .55f)
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, CathodeCyan.copy(alpha = .22f), Color.Transparent)),
            start = Offset(0f, streakY),
            end = Offset(size.width, streakY),
            strokeWidth = 4f,
        )
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
