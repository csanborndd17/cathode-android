package com.twelvepts.cathode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.twelvepts.cathode.playback.PlaybackState
import com.twelvepts.cathode.playback.PlayerConnection

@Composable
fun NowPlayingScreen(state: PlaybackState, player: PlayerConnection, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = CathodeBlack) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Back", tint = CathodeCyan) }
                Text("NOW PLAYING // CH ${state.mediaItemIndex + 1}", color = CathodeMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.weight(.25f))
            AsyncImage(
                model = state.artworkUri,
                contentDescription = "Album cover",
                modifier = Modifier.fillMaxWidth().border(1.dp, CathodeCyan).background(CathodePanel),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(28.dp))
            Text(state.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(state.artist, color = CathodeCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Slider(
                value = state.positionMs.toFloat().coerceIn(0f, state.durationMs.coerceAtLeast(1).toFloat()),
                onValueChange = { player.seekTo(it.toLong()) },
                valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(thumbColor = CathodeCyan, activeTrackColor = CathodeCyan, inactiveTrackColor = CathodeDim),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(state.positionMs), color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
                Text("-${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0))}", color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.setShuffle(!state.shuffleEnabled) }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffleEnabled) CathodeCyan else CathodeMuted)
                }
                IconButton(onClick = player::previous) { Icon(Icons.Default.SkipPrevious, "Previous", tint = CathodeText, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = player::togglePlayPause, modifier = Modifier.size(72.dp).border(1.dp, CathodeCyan, RoundedCornerShape(50))) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = CathodeCyan, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = player::next) { Icon(Icons.Default.SkipNext, "Next", tint = CathodeText, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = player::cycleRepeat) {
                    Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat", tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) CathodeMuted else CathodeCyan)
                }
            }
            Spacer(Modifier.weight(.5f))
            Text("12PTS AUDIO TERMINAL // LOSSLESS PATH ACTIVE", color = CathodeDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), textAlign = TextAlign.Center)
        }
    }
}
