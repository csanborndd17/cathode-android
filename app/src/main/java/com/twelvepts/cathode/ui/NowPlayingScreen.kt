package com.twelvepts.cathode.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.twelvepts.cathode.playback.PlaybackState
import com.twelvepts.cathode.playback.PlayerConnection

@Composable
fun NowPlayingScreen(state: PlaybackState, player: PlayerConnection, animations: Boolean, onDismiss: () -> Unit) {
    val view = LocalView.current
    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    BackHandler(onBack = onDismiss)

    Surface(Modifier.fillMaxSize(), color = CathodeBlack) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(
                targetState = state.artworkUri,
                animationSpec = tween(if (animations) 650 else 0),
                label = "now-playing-background",
            ) { artwork ->
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(34.dp).alpha(.32f),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to CathodeBlack.copy(alpha = .35f),
                        .48f to CathodeBlack.copy(alpha = .62f),
                        1f to CathodeBlack,
                    ),
                ),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Collapse player", tint = CathodeCyan) }
                    Text(
                        "Now playing",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.height(10.dp))
                Crossfade(
                    targetState = state.artworkUri,
                    animationSpec = tween(if (animations) 450 else 0),
                    label = "now-playing-artwork",
                ) { artwork ->
                    AsyncImage(
                        model = artwork,
                        contentDescription = "Album cover",
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(CathodePanel),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(state.artist, color = CathodeCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = state.positionMs.toFloat().coerceIn(0f, state.durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = CathodeCyan,
                        activeTrackColor = CathodeCyan,
                        inactiveTrackColor = CathodeDim,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(state.positionMs), color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "-${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0))}",
                        color = CathodeMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        haptic()
                        player.setShuffle(!state.shuffleEnabled)
                    }) {
                        Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffleEnabled) CathodeCyan else CathodeMuted)
                    }
                    IconButton(onClick = {
                        haptic()
                        player.previous()
                    }) {
                        Icon(Icons.Default.SkipPrevious, "Previous", tint = CathodeText, modifier = Modifier.size(34.dp))
                    }
                    IconButton(
                        onClick = {
                            haptic()
                            player.togglePlayPause()
                        },
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(CathodeCyan),
                    ) {
                        Crossfade(
                            targetState = state.isPlaying,
                            animationSpec = tween(if (animations) 180 else 0),
                            label = "play-pause",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playing) "Pause" else "Play",
                                tint = CathodeBlack,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }
                    IconButton(onClick = {
                        haptic()
                        player.next()
                    }) {
                        Icon(Icons.Default.SkipNext, "Next", tint = CathodeText, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = {
                        haptic()
                        player.cycleRepeat()
                    }) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            "Repeat",
                            tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) CathodeMuted else CathodeCyan,
                        )
                    }
                }
                Text(
                    "LOCAL PLAYBACK",
                    color = CathodeDim,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
