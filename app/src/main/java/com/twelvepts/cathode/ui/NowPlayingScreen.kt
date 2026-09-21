package com.twelvepts.cathode.ui

import android.view.HapticFeedbackConstants
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.twelvepts.cathode.playback.AudioLabState
import com.twelvepts.cathode.playback.PlaybackState
import com.twelvepts.cathode.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NowPlayingScreen(state: PlaybackState, player: PlayerConnection, animations: Boolean, onDismiss: () -> Unit) {
    val view = LocalView.current
    var showQueue by remember { mutableStateOf(false) }
    var showAudioLab by remember { mutableStateOf(false) }
    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    BackHandler { when { showAudioLab -> showAudioLab = false; showQueue -> showQueue = false; else -> onDismiss() } }

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
                    modifier = Modifier.fillMaxSize().blur(22.dp).alpha(.54f),
                    contentScale = ContentScale.Crop,
                )
            }
            SignalDust(animations)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to CathodeBlack.copy(alpha = .20f),
                        .70f to CathodeBlack.copy(alpha = .42f),
                        1f to CathodeBlack.copy(alpha = .92f),
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
                    Row {
                        IconButton(onClick = { showAudioLab = true }) { Icon(Icons.Default.Equalizer, "Open Audio Lab", tint = CathodeCyan) }
                        IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, "Open queue", tint = CathodeCyan) }
                    }
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
                AudioDetailsCard(state)
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
    if (showQueue) QueueScreen(state, player) { showQueue = false }
    if (showAudioLab) AudioLabScreen(player) { showAudioLab = false }
}

@Composable
private fun QueueScreen(state: PlaybackState, player: PlayerConnection, onClose: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = CathodeBlack) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back to now playing", tint = CathodeCyan) }
                Column(Modifier.weight(1f)) {
                    Text("PLAYBACK QUEUE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${state.queue.size} tracks", color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = player::removeQueueDuplicates, enabled = state.queue.map { it.mediaId }.distinct().size < state.queue.size) {
                    Text("Deduplicate")
                }
                TextButton(onClick = player::clearUpcoming, enabled = state.mediaItemIndex + 1 < state.queue.size) {
                    Text("Clear")
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.queue, key = { index, item -> "${item.mediaId}-$index" }) { index, item ->
                    val current = index == state.mediaItemIndex
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (current) CathodeCyan.copy(alpha = .12f) else CathodeBlack)
                            .clickable { player.playQueueIndex(index) }
                            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(item.artworkUri, null, Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(CathodePanel), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.title, color = if (current) CathodeCyan else CathodeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.artist, color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        IconButton(onClick = { player.moveQueueItem(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, "Move up", tint = CathodeMuted)
                        }
                        IconButton(onClick = { player.moveQueueItem(index, index + 1) }, enabled = index < state.queue.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, "Move down", tint = CathodeMuted)
                        }
                        IconButton(onClick = { player.removeQueueItem(index) }, enabled = state.queue.size > 1) {
                            Icon(Icons.Default.Delete, "Remove from queue", tint = CathodeMuted)
                        }
                    }
                }
            }
        }
    }
}


private data class AudioDetails(val sampleRate: String = "—", val bitrate: String = "—", val bitDepth: String = "—")

@Composable
private fun AudioDetailsCard(state: PlaybackState) {
    val context = LocalContext.current
    val entry = state.queue.getOrNull(state.mediaItemIndex)
    var details by remember(entry?.sourceUri) { mutableStateOf(AudioDetails()) }
    LaunchedEffect(entry?.sourceUri) {
        val uri = entry?.sourceUri ?: return@LaunchedEffect
        details = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    AudioDetails(
                        sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let { "${it / 1000.0} kHz" } ?: "—",
                        bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { "${it / 1000} kbps" } ?: "—",
                        bitDepth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.let { "$it-bit" } ?: "—",
                    )
                } finally { retriever.release() }
            }.getOrDefault(AudioDetails())
        }
    }
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val route = when {
        manager.isBluetoothA2dpOn -> "Bluetooth"
        manager.isWiredHeadsetOn -> "Wired / USB audio"
        else -> "Device output"
    }
    Card(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("SIGNAL DETAILS", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(listOf(entry?.mimeType?.substringAfter('/')?.uppercase() ?: "AUDIO", details.sampleRate, details.bitDepth, details.bitrate).joinToString(" · "), color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
            Text(route, color = CathodeDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun AudioLabScreen(player: PlayerConnection, onClose: () -> Unit) {
    val state by player.audioLab.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = CathodeBlack) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back to now playing", tint = CathodeCyan) }
                Column(Modifier.weight(1f)) {
                    Text("AUDIO LAB", color = CathodeCyan, fontWeight = FontWeight.Bold)
                    Text("Output equalizer", color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
                }
                Switch(checked = state.enabled, onCheckedChange = player::setEqualizerEnabled, enabled = state.available)
            }
            if (!state.available) {
                Text("This output does not expose an Android audio-effects session. Equalizer controls are unavailable for this route.", color = CathodeMuted, modifier = Modifier.padding(20.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Text("PRESETS", color = CathodeCyan, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.presets.size) { index ->
                                AssistChip(onClick = { player.useEqualizerPreset(index) }, label = { Text(state.presets[index]) }, enabled = state.enabled)
                            }
                        }
                    }
                    item {
                        Text("FREQUENCY BANDS", color = CathodeCyan, fontWeight = FontWeight.Bold)
                        state.bandLevels.forEachIndexed { index, level ->
                            val frequency = state.centerFrequenciesHz.getOrNull(index) ?: 0
                            Column {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(frequencyLabel(frequency), Modifier.weight(1f))
                                    Text("${level / 100f} dB", color = CathodeMuted)
                                }
                                Slider(
                                    value = level.toFloat(),
                                    onValueChange = { player.setBandLevel(index, it.toInt().toShort()) },
                                    valueRange = state.minimumLevel.toFloat()..state.maximumLevel.toFloat(),
                                    enabled = state.enabled,
                                )
                            }
                        }
                    }
                    item {
                        Text("BASS BOOST", color = CathodeCyan, fontWeight = FontWeight.Bold)
                        Slider(
                            value = state.bassBoost.toFloat(),
                            onValueChange = { player.setBassBoost(it.toInt().toShort()) },
                            valueRange = 0f..1000f,
                            enabled = state.enabled,
                        )
                        Text("${state.bassBoost / 10}%", color = CathodeMuted)
                    }
                    item {
                        Text("Audio effects depend on Android, the decoder, and the connected output. Some Bluetooth and USB devices may apply their own processing afterward.", color = CathodeMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 24.dp))
                    }
                }
            }
        }
    }
}

private fun frequencyLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000f} kHz" else "$hz Hz"
