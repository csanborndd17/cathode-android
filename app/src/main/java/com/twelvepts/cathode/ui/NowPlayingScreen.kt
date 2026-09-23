package com.twelvepts.cathode.ui

import android.view.HapticFeedbackConstants
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.twelvepts.cathode.playback.AudioLabState
import com.twelvepts.cathode.playback.PlaybackState
import com.twelvepts.cathode.playback.PlayerConnection
import com.twelvepts.cathode.model.AudioTrack
import com.twelvepts.cathode.data.LyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import java.util.Calendar

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    player: PlayerConnection,
    settings: CathodeSettings,
    currentTrack: AudioTrack?,
    libraryTracks: List<AudioTrack>,
    onSaveLyrics: (AudioTrack, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val animations = settings.animations
    val view = LocalView.current
    var showQueue by remember { mutableStateOf(false) }
    var showAudioLab by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var sleepAmount by remember { mutableStateOf("") }
    var sleepUnit by remember { mutableStateOf("minutes") }
    val nowPlayingScroll = rememberScrollState()
    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    BackHandler { when { showSleepTimer -> showSleepTimer = false; showLyrics -> showLyrics = false; showAudioLab -> showAudioLab = false; showQueue -> showQueue = false; else -> onDismiss() } }

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
                    .verticalScroll(nowPlayingScroll)
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
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    item {
                        IconButton(onClick = { showLyrics = true }) {
                            Icon(Icons.Default.Lyrics, "Open lyrics", tint = if (state.lyrics.isBlank()) CathodeMuted else CathodeCyan)
                        }
                    }
                    item {
                        IconButton(onClick = { showSleepTimer = true }) {
                            Icon(Icons.Default.Bedtime, "Sleep timer", tint = if (state.sleepTimerEndEpochMs > 0) CathodeCyan else CathodeMuted)
                        }
                    }
                    item {
                        IconButton(onClick = { showAudioLab = true }) { Icon(Icons.Default.Equalizer, "Open Audio Lab", tint = CathodeCyan) }
                    }
                    item {
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
                state.playbackError?.let {
                    Text(it, color = CathodeError, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                }
                state.replayGainDb?.let { gain ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ReplayGain ${"%+.2f".format(gain)} dB", Modifier.weight(1f), color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
                        Switch(checked = state.replayGainEnabled, onCheckedChange = player::setReplayGainEnabled)
                    }
                }
                Spacer(Modifier.height(10.dp))
                WaveformScrubber(
                    title = state.title,
                    cacheKey = state.queue.getOrNull(state.mediaItemIndex)?.mediaId,
                    sourceUri = state.queue.getOrNull(state.mediaItemIndex)?.sourceUri,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    style = settings.waveformStyle,
                    color = waveformColor(settings),
                    onSeek = player::seekTo,
                )
                if (!state.isSeekable) {
                    Text(
                        "This file has no usable seek map. Playback works, but scrubbing may be unavailable.",
                        color = CathodeMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
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
                AnimatedVisibility(
                    visible = nowPlayingScroll.value > 60,
                    enter = fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 2 },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 },
                ) { AudioDetailsCard(state) }
                Spacer(Modifier.height(92.dp))
            }
        }
    }
    AnimatedVisibility(
        visible = showLyrics,
        enter = fadeIn(tween(if (animations) 260 else 0)) + slideInVertically(tween(if (animations) 320 else 0)) { it / 10 },
        exit = fadeOut(tween(if (animations) 220 else 0)) + slideOutVertically(tween(if (animations) 260 else 0)) { it / 12 },
        label = "lyrics-overlay",
    ) { LyricsScreen(state, player, currentTrack, libraryTracks, onSaveLyrics) { showLyrics = false } }
    if (showQueue) QueueScreen(state, player) { showQueue = false }
    if (showAudioLab) AudioLabScreen(player) { showAudioLab = false }
    if (showSleepTimer) AlertDialog(
        onDismissRequest = { showSleepTimer = false },
        title = { Text("Sleep timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cathode will keep playing in the background, then pause automatically after the selected time.", color = CathodeMuted)
                val remaining = (state.sleepTimerEndEpochMs - System.currentTimeMillis()).coerceAtLeast(0)
                if (remaining > 0) Text("Pausing in about " + ((remaining + 59_999) / 60_000) + " minutes.", color = CathodeCyan)
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    TextButton(onClick = { player.setSleepTimer(minutes); showSleepTimer = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("$minutes minutes")
                    }
                }
                state.sleepTimerPresetsSeconds.forEach { seconds ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { player.setSleepTimerSeconds(seconds); showSleepTimer = false }, modifier = Modifier.weight(1f)) {
                            Text(formatSleepDuration(seconds))
                        }
                        IconButton(onClick = { player.removeSleepTimerPreset(seconds) }) {
                            Icon(Icons.Default.Delete, "Delete sleep preset", tint = CathodeMuted)
                        }
                    }
                }
                OutlinedTextField(
                    value = sleepAmount,
                    onValueChange = { sleepAmount = it.filter(Char::isDigit).take(6) },
                    label = { Text("Custom amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("seconds", "minutes", "hours").forEach { unit ->
                        AssistChip(onClick = { sleepUnit = unit }, label = { Text(unit) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        sleepAmount.toLongOrNull()?.let { amount ->
                            val seconds = amount * when (sleepUnit) { "hours" -> 3600L; "minutes" -> 60L; else -> 1L }
                            player.setSleepTimerSeconds(seconds)
                            showSleepTimer = false
                        }
                    }) { Text("Start custom") }
                    TextButton(onClick = {
                        sleepAmount.toLongOrNull()?.let { amount ->
                            val seconds = amount * when (sleepUnit) { "hours" -> 3600L; "minutes" -> 60L; else -> 1L }
                            player.addSleepTimerPreset(seconds)
                        }
                    }) { Text("Save preset") }
                }
                val context = LocalContext.current
                TextButton(onClick = {
                    val now = Calendar.getInstance()
                    TimePickerDialog(context, { _, hour, minute ->
                        val target = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                        }
                        player.setSleepTimerAt(target.timeInMillis)
                        showSleepTimer = false
                    }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Pause at a time of day…") }
            }
        },
        confirmButton = {
            if (state.sleepTimerEndEpochMs > 0) TextButton(onClick = { player.cancelSleepTimer(); showSleepTimer = false }) { Text("Cancel timer") }
        },
        dismissButton = { TextButton(onClick = { showSleepTimer = false }) { Text("Close") } },
    )
}

private data class LyricLine(val timeMs: Long?, val text: String)

@Composable
private fun LyricsScreen(
    state: PlaybackState,
    player: PlayerConnection,
    currentTrack: AudioTrack?,
    libraryTracks: List<AudioTrack>,
    onSaveLyrics: (AudioTrack, String) -> Unit,
    onClose: () -> Unit,
) {
    val lines = remember(state.lyrics) { parseLyrics(state.lyrics) }
    val activeIndex = lines.indexOfLast { it.timeMs != null && it.timeMs <= state.positionMs }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var lookupRunning by remember(currentTrack?.stableKey) { mutableStateOf(false) }
    var lookupMessage by remember(currentTrack?.stableKey) { mutableStateOf<String?>(null) }
    var showBatch by remember { mutableStateOf(false) }
    var batchRunning by remember { mutableStateOf(false) }
    var batchProgress by remember { mutableStateOf(0) }
    var batchTotal by remember { mutableStateOf(0) }
    var batchFailures by remember { mutableStateOf<List<String>>(emptyList()) }
    var batchFinished by remember { mutableStateOf(false) }
    val trackKey = state.queue.getOrNull(state.mediaItemIndex)?.mediaId
    var following by remember(trackKey) { mutableStateOf(true) }
    val manualScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) following = false
                return Offset.Zero
            }
        }
    }
    suspend fun centerActiveLine() {
        if (activeIndex < 0) return
        var info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (info == null) {
            listState.scrollToItem(activeIndex)
            info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex } ?: return
        }
        val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
        listState.animateScrollBy((info.offset + info.size / 2 - viewportCenter).toFloat())
    }
    LaunchedEffect(activeIndex, following) {
        if (following) centerActiveLine()
    }
    Surface(Modifier.fillMaxSize(), color = CathodeBlack) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = state.artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(24.dp).alpha(.66f),
                contentScale = ContentScale.Crop,
            )
            SignalDust(true)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to CathodeBlack.copy(alpha = .22f), 1f to CathodeBlack.copy(alpha = .78f))))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Close lyrics", tint = CathodeCyan) }
                Column(Modifier.weight(1f)) {
                    Text("Lyrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(state.title, color = CathodeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(
                    enabled = currentTrack != null && !lookupRunning,
                    onClick = {
                        val track = currentTrack ?: return@TextButton
                        lookupRunning = true
                        lookupMessage = null
                        scope.launch {
                            LyricsRepository.find(track).fold(
                                onSuccess = {
                                    onSaveLyrics(track, it.lyrics)
                                    lookupMessage = if (it.synchronized) "Synchronized lyrics saved." else "Plain lyrics saved."
                                },
                                onFailure = { lookupMessage = it.message ?: "Lyrics were not found." },
                            )
                            lookupRunning = false
                        }
                    },
                ) { Text(if (lookupRunning) "Searching…" else if (state.lyrics.isBlank()) "Find and save" else "Find replacement") }
                TextButton(onClick = { showBatch = true }) { Text("Find missing lyrics") }
            }
            if (lines.any { it.timeMs != null }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("TIMING", color = CathodeMuted, style = MaterialTheme.typography.labelSmall)
                    TextButton(
                        enabled = currentTrack != null,
                        onClick = { currentTrack?.let { onSaveLyrics(it, shiftLrcTimestamps(state.lyrics, -1_000L)) } },
                    ) { Text("−1s") }
                    TextButton(
                        enabled = currentTrack != null,
                        onClick = { currentTrack?.let { onSaveLyrics(it, shiftLrcTimestamps(state.lyrics, 1_000L)) } },
                    ) { Text("+1s") }
                }
            }
            lookupMessage?.let { Text(it, color = CathodeMuted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            if (lines.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No local lyrics yet. Edit this track's metadata from Library to add plain text or LRC lyrics.", color = CathodeMuted, textAlign = TextAlign.Center)
                }
            } else BoxWithConstraints(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(manualScrollConnection),
                    contentPadding = PaddingValues(vertical = maxHeight * .40f),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    itemsIndexed(lines) { index, line ->
                        val active = index == activeIndex
                        val scale by animateFloatAsState(
                            if (active) 1f else .82f,
                            tween(420, easing = FastOutSlowInEasing),
                            label = "lyric-scale",
                        )
                        val lineColor by animateColorAsState(
                            if (active) CathodeCyan else CathodeText.copy(alpha = if (line.timeMs == null) .82f else .48f),
                            tween(420, easing = FastOutSlowInEasing),
                            label = "lyric-color",
                        )
                        Column(
                            Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
                                .clickable(enabled = line.timeMs != null) { line.timeMs?.let(player::seekTo) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            line.timeMs?.let { Text(formatLyricTimestamp(it), color = lineColor.copy(alpha = .58f), style = MaterialTheme.typography.labelSmall) }
                            Text(
                                line.text,
                                color = lineColor,
                                style = MaterialTheme.typography.headlineLarge.copy(shadow = Shadow(CathodeBlack, Offset(0f, 3f), 9f)),
                                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (!following && activeIndex >= 0) {
                    SmallFloatingActionButton(
                        onClick = { following = true },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                        containerColor = CathodeCyan,
                        contentColor = CathodeBlack,
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, "Recenter on current lyric")
                    }
                }
            }
        }
        }
    }
    if (showBatch) AlertDialog(
        onDismissRequest = { if (!batchRunning) showBatch = false },
        title = { Text("Find missing lyrics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    batchRunning -> {
                        Text("Searching $batchProgress of $batchTotal", color = CathodeCyan)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (batchTotal == 0) 0f else batchProgress.toFloat() / batchTotal },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    batchFinished && batchTotal == 0 -> Text("Every track already has saved lyrics.", color = CathodeMuted)
                    batchFinished -> Text("Checked $batchProgress tracks. ${batchFailures.size} could not be matched.", color = CathodeMuted)
                    else -> Text("Cathode will search sequentially for every track without saved lyrics. Matches are saved immediately.", color = CathodeMuted)
                }
                if (batchFailures.isNotEmpty()) {
                    Text("NOT FOUND", color = CathodeCyan, fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(batchFailures.size) { index -> Text(batchFailures[index], modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            if (!batchRunning && !batchFinished) TextButton(onClick = {
                val targets = libraryTracks.filter { it.lyrics.isBlank() }
                batchTotal = targets.size
                batchProgress = 0
                batchFailures = emptyList()
                batchRunning = true
                batchFinished = false
                scope.launch {
                    targets.forEachIndexed { index, track ->
                        LyricsRepository.find(track).fold(
                            onSuccess = { onSaveLyrics(track, it.lyrics) },
                            onFailure = { batchFailures = batchFailures + "${track.title} — ${track.artist}" },
                        )
                        batchProgress = index + 1
                        delay(120)
                    }
                    batchRunning = false
                    batchFinished = true
                }
            }) { Text("Start") }
            else if (!batchRunning) TextButton(onClick = { showBatch = false }) { Text("Done") }
        },
        dismissButton = { if (!batchRunning && !batchFinished) TextButton(onClick = { showBatch = false }) { Text("Cancel") } },
    )
}

private fun parseLyrics(value: String): List<LyricLine> = value.lineSequence().mapNotNull { raw ->
    val line = raw.trim()
    if (line.isBlank()) return@mapNotNull null
    val match = Regex("^\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)$").find(line)
    if (match == null) LyricLine(null, line) else {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        LyricLine((minutes * 60L + seconds) * 1000L + fraction, match.groupValues[4].trim())
    }
}.toList()

private fun shiftLrcTimestamps(value: String, deltaMs: Long): String = value.lineSequence().joinToString("\n") { raw ->
    val match = Regex("^\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)$").find(raw.trim()) ?: return@joinToString raw
    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    val shifted = ((minutes * 60L + seconds) * 1000L + fraction + deltaMs).coerceAtLeast(0L)
    "[%02d:%02d.%02d]%s".format(shifted / 60_000L, shifted / 1_000L % 60L, shifted % 1_000L / 10L, match.groupValues[4])
}

private fun formatLyricTimestamp(timeMs: Long): String = "%02d:%02d".format(timeMs / 60_000L, timeMs / 1_000L % 60L)

private fun formatSleepDuration(seconds: Long): String = when {
    seconds % 3600L == 0L -> "${seconds / 3600L} hours"
    seconds % 60L == 0L -> "${seconds / 60L} minutes"
    else -> "$seconds seconds"
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
                    Text("QUEUE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${(state.queue.size - state.mediaItemIndex - 1).coerceAtLeast(0)} tracks upcoming", color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = player::clearUpcoming, enabled = state.mediaItemIndex + 1 < state.queue.size) {
                    Text("Clear upcoming")
                }
            }
            val current = state.queue.getOrNull(state.mediaItemIndex)
            if (current != null) {
                Text("NOW PLAYING", color = CathodeCyan, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(CathodeCyan.copy(alpha = .12f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(current.artworkUri, null, Modifier.size(62.dp).clip(MaterialTheme.shapes.small).background(CathodePanel), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(current.title, color = CathodeCyan, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current.artist, color = CathodeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = player::previous) { Icon(Icons.Default.SkipPrevious, "Previous", tint = CathodeText) }
                    IconButton(
                        onClick = player::togglePlayPause,
                        modifier = Modifier.size(54.dp).clip(CircleShape).background(CathodeCyan),
                    ) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = CathodeBlack) }
                    IconButton(onClick = player::next) { Icon(Icons.Default.SkipNext, "Next", tint = CathodeText) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 18.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("UP NEXT", Modifier.weight(1f), color = CathodeCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = player::removeQueueDuplicates, enabled = state.queue.map { it.mediaId }.distinct().size < state.queue.size) { Text("Remove duplicates") }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                val upcoming = state.queue.drop(state.mediaItemIndex + 1)
                itemsIndexed(upcoming, key = { index, item -> "${item.mediaId}-${state.mediaItemIndex + 1 + index}" }) { offset, item ->
                    val index = state.mediaItemIndex + 1 + offset
                    var menu by remember(item.mediaId, index) { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clickable { player.playQueueIndex(index) }
                            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${offset + 1}", color = CathodeDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 10.dp))
                        AsyncImage(item.artworkUri, null, Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(CathodePanel), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.title, color = CathodeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.artist, color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Queue actions", tint = CathodeMuted) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Play now") }, onClick = { player.playQueueIndex(index); menu = false })
                                if (offset > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { player.moveQueueItem(index, index - 1); menu = false })
                                if (offset < upcoming.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = { player.moveQueueItem(index, index + 1); menu = false })
                                if (offset > 0) DropdownMenuItem(text = { Text("Move to next") }, onClick = { player.moveQueueItem(index, state.mediaItemIndex + 1); menu = false })
                                DropdownMenuItem(text = { Text("Remove") }, onClick = { player.removeQueueItem(index); menu = false })
                            }
                        }
                    }
                }
                if (upcoming.isEmpty()) item { Text("The queue ends after this track.", color = CathodeMuted, modifier = Modifier.padding(22.dp)) }
            }
        }
    }
}


@Composable
private fun WaveformScrubber(
    title: String,
    cacheKey: String?,
    sourceUri: android.net.Uri?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    style: WaveformStyle,
    color: androidx.compose.ui.graphics.Color,
    onSeek: (Long) -> Unit,
) {
    val context = LocalContext.current
    var widthPx by remember { mutableStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val seed = remember(title) { abs(title.hashCode() % 23) + 5 }
    val placeholder = remember(seed) {
        List(84) { index ->
            (.20f + abs(sin((index + 1) * seed * .071f)).toFloat() * .68f + (index % 7) * .012f).coerceAtMost(1f)
        }
    }
    var amplitudes by remember(cacheKey) { mutableStateOf(placeholder) }
    LaunchedEffect(cacheKey, sourceUri) {
        amplitudes = placeholder
        if (cacheKey != null && sourceUri != null) {
            withContext(Dispatchers.IO) { WaveformRepository.load(context, cacheKey, sourceUri) }?.let { amplitudes = it }
        }
    }
    fun seekAt(x: Float) {
        val fraction = (x / widthPx).coerceIn(0f, 1f)
        dragFraction = fraction
        if (durationMs > 0) onSeek((fraction * durationMs).toLong())
    }
    Box(Modifier.fillMaxWidth().height(84.dp)) {
      Canvas(
        Modifier.fillMaxWidth()
            .height(74.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(durationMs) { detectTapGestures(onPress = { seekAt(it.x); tryAwaitRelease(); dragFraction = null }) }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { seekAt(it.x) },
                    onDrag = { change, _ -> seekAt(change.position.x) },
                    onDragEnd = { dragFraction = null },
                    onDragCancel = { dragFraction = null },
                )
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(positionMs.toFloat(), 0f..durationMs.coerceAtLeast(1).toFloat())
                setProgress { value -> onSeek(value.toLong().coerceIn(0, durationMs.coerceAtLeast(0))); true }
            },
    ) {
        val gap = 2.5f
        val barWidth = (size.width - gap * (amplitudes.size - 1)) / amplitudes.size
        amplitudes.forEachIndexed { index, amplitude ->
            val fraction = index.toFloat() / amplitudes.lastIndex
            val active = fraction <= progress
            val baseHeight = size.height * amplitude
            val touch = dragFraction
            val pulse = when {
                touch != null && abs(fraction - touch) < .07f -> 1.32f
                isPlaying && abs(fraction - progress) < .018f -> 1.18f
                else -> 1f
            }
            val height = (baseHeight * pulse).coerceAtMost(size.height)
            val left = index * (barWidth + gap)
            val signalColor = if (active) color else color.copy(alpha = .24f)
            when (style) {
                WaveformStyle.BARS -> drawRoundRect(signalColor, androidx.compose.ui.geometry.Offset(left, size.height - height), androidx.compose.ui.geometry.Size(barWidth.coerceAtLeast(1f), height), CornerRadius(barWidth, barWidth))
                WaveformStyle.MIRROR -> drawRoundRect(signalColor, androidx.compose.ui.geometry.Offset(left, (size.height - height) / 2f), androidx.compose.ui.geometry.Size(barWidth.coerceAtLeast(1f), height), CornerRadius(barWidth, barWidth))
                WaveformStyle.DOTS -> drawCircle(signalColor, (2f + amplitude * 5f) * pulse, androidx.compose.ui.geometry.Offset(left + barWidth / 2f, size.height / 2f))
                WaveformStyle.LINE -> if (index < amplitudes.lastIndex) {
                    val nextX = (index + 1) * (barWidth + gap) + barWidth / 2f
                    val nextY = size.height / 2f - (amplitudes[index + 1] - .5f) * size.height * .82f
                    drawLine(signalColor, androidx.compose.ui.geometry.Offset(left + barWidth / 2f, size.height / 2f - (amplitude - .5f) * size.height * .82f), androidx.compose.ui.geometry.Offset(nextX, nextY), strokeWidth = 3.5f)
                }
            }
        }
      }
      dragFraction?.let { fraction ->
          Text(
              formatDuration((fraction * durationMs).toLong()),
              color = CathodeBlack,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.align(Alignment.TopStart)
                  .padding(start = ((fraction * 280f).coerceIn(0f, 260f)).dp)
                  .background(color, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
          )
      }
    }
}

private fun waveformColor(settings: CathodeSettings): androidx.compose.ui.graphics.Color = when (settings.waveformColorPreset) {
    WaveformColorPreset.ACCENT -> settings.customAccentArgb?.let { androidx.compose.ui.graphics.Color(it) } ?: CathodeCyan
    WaveformColorPreset.CYAN -> androidx.compose.ui.graphics.Color(0xFF00E5FF)
    WaveformColorPreset.AMBER -> androidx.compose.ui.graphics.Color(0xFFFFB300)
    WaveformColorPreset.MAGENTA -> androidx.compose.ui.graphics.Color(0xFFFF4FD8)
    WaveformColorPreset.WHITE -> androidx.compose.ui.graphics.Color.White
    WaveformColorPreset.CUSTOM -> androidx.compose.ui.graphics.Color(settings.waveformCustomArgb)
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
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SIGNAL DETAILS", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(listOf(entry?.mimeType?.substringAfter('/')?.uppercase() ?: "AUDIO", details.sampleRate, details.bitDepth, details.bitrate).joinToString(" · "), color = CathodeMuted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            Text(route, color = CathodeDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
            Text("LOCAL PLAYBACK", color = CathodeDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AudioLabScreen(player: PlayerConnection, onClose: () -> Unit) {
    val state by player.audioLab.collectAsStateWithLifecycle()
    var namingPreset by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<String?>(null) }
    var presetName by remember { mutableStateOf("") }
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
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("MY PRESETS", Modifier.weight(1f), color = CathodeCyan, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { presetName = ""; editingPreset = null; namingPreset = true }, enabled = state.enabled) { Text("Save current") }
                        }
                        state.userPresets.forEach { preset ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { player.applyUserEqualizerPreset(preset.name) }, enabled = state.enabled, modifier = Modifier.weight(1f)) {
                                    Text(preset.name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                                }
                                IconButton(onClick = { presetName = preset.name; editingPreset = preset.name; namingPreset = true }) {
                                    Icon(Icons.Default.Edit, "Rename ${preset.name}", tint = CathodeMuted)
                                }
                                IconButton(onClick = { player.deleteUserEqualizerPreset(preset.name) }) {
                                    Icon(Icons.Default.Delete, "Delete ${preset.name}", tint = CathodeMuted)
                                }
                            }
                        }
                        if (state.userPresets.isEmpty()) Text("Save your current bands and bass boost as a reusable preset.", color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
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
    if (namingPreset) AlertDialog(
        onDismissRequest = { namingPreset = false },
        title = { Text(if (editingPreset == null) "Save EQ preset" else "Rename EQ preset") },
        text = { OutlinedTextField(presetName, { presetName = it.take(40) }, label = { Text("Preset name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val old = editingPreset
            if (old == null) player.saveUserEqualizerPreset(presetName) else player.renameUserEqualizerPreset(old, presetName)
            namingPreset = false
        }, enabled = presetName.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = { namingPreset = false }) { Text("Cancel") } },
    )
}

private fun frequencyLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000f} kHz" else "$hz Hz"
