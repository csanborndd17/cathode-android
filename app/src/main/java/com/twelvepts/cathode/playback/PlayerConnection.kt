package com.twelvepts.cathode.playback

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.twelvepts.cathode.model.AudioTrack
import com.twelvepts.cathode.model.AudioQuality
import com.twelvepts.cathode.data.CathodeDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueEntry(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val mimeType: String? = null,
    val sourceUri: Uri? = null,
)

data class AudioLabState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bandLevels: List<Short> = emptyList(),
    val centerFrequenciesHz: List<Int> = emptyList(),
    val minimumLevel: Short = -1500,
    val maximumLevel: Short = 1500,
    val presets: List<String> = emptyList(),
    val bassBoost: Short = 0,
)

data class PlaybackState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artworkUri: Uri? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isSeekable: Boolean = true,
    val mediaItemIndex: Int = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val sleepTimerEndEpochMs: Long = 0,
    val sleepTimerPresetsSeconds: List<Long> = emptyList(),
    val playbackError: String? = null,
    val lyrics: String = "",
    val replayGainDb: Float? = null,
    val replayGainEnabled: Boolean = false,
    val transitionFadeEnabled: Boolean = false,
    val transitionFadeSeconds: Int = 3,
    val audioQuality: AudioQuality = AudioQuality.UNKNOWN,
)

class PlayerConnection(context: Context) : Player.Listener {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _audioLab = MutableStateFlow(AudioLabState())
    val audioLab: StateFlow<AudioLabState> = _audioLab.asStateFlow()
    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private val audioPreferences = appContext.getSharedPreferences("audio_lab", Context.MODE_PRIVATE)
    private val playbackPreferences = appContext.getSharedPreferences("playback_session", Context.MODE_PRIVATE)
    private var playbackError: String? = null

    init {
        val token = SessionToken(appContext, ComponentName(appContext, CathodePlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }.getOrNull()?.let {
                controller = it
                it.addListener(this)
                attachAudioLab(it.audioSessionId)
                publishState()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun play(tracks: List<AudioTrack>, selected: AudioTrack) {
        val index = tracks.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        controller?.apply {
            setMediaItems(tracks.map(AudioTrack::toMediaItem), index, 0)
            prepare()
            play()
        }
    }

    fun addToQueue(track: AudioTrack) { controller?.addMediaItem(track.toMediaItem()) }
    fun playNext(track: AudioTrack) {
        controller?.let { it.addMediaItem((it.currentMediaItemIndex + 1).coerceAtMost(it.mediaItemCount), track.toMediaItem()) }
    }
    fun togglePlayPause() = controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.let { if (it.currentPosition > 4_000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    fun setShuffle(enabled: Boolean) { controller?.shuffleModeEnabled = enabled }
    fun playQueueIndex(index: Int) = controller?.let {
        if (index in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }
    fun removeQueueItem(index: Int) = controller?.let {
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }
    fun moveQueueItem(from: Int, to: Int) = controller?.let {
        if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount && from != to) it.moveMediaItem(from, to)
    }
    fun removeQueueDuplicates() = controller?.let { player ->
        val seen = mutableSetOf<String>()
        for (index in player.mediaItemCount - 1 downTo 0) {
            val id = player.getMediaItemAt(index).mediaId
            if (!seen.add(id) && index != player.currentMediaItemIndex) player.removeMediaItem(index)
        }
    }
    fun clearUpcoming() = controller?.let {
        val current = it.currentMediaItemIndex
        if (current >= 0 && current + 1 < it.mediaItemCount) it.removeMediaItems(current + 1, it.mediaItemCount)
    }
    fun cycleRepeat() {
        controller?.repeatMode = when (controller?.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSleepTimer(minutes: Int) {
        setSleepTimerSeconds(minutes.toLong() * 60L)
    }

    fun setSleepTimerSeconds(seconds: Long) {
        val end = if (seconds <= 0) 0L else System.currentTimeMillis() + seconds * 1_000L
        playbackPreferences.edit().putLong("sleep_end", end).apply()
        publishState()
    }

    fun setSleepTimerAt(epochMs: Long) {
        playbackPreferences.edit().putLong("sleep_end", epochMs.coerceAtLeast(System.currentTimeMillis())).apply()
        publishState()
    }

    fun addSleepTimerPreset(seconds: Long) {
        if (seconds <= 0) return
        val presets = playbackPreferences.getStringSet("sleep_presets", emptySet()).orEmpty() + seconds.toString()
        playbackPreferences.edit().putStringSet("sleep_presets", presets).apply()
        publishState()
    }

    fun removeSleepTimerPreset(seconds: Long) {
        val presets = playbackPreferences.getStringSet("sleep_presets", emptySet()).orEmpty() - seconds.toString()
        playbackPreferences.edit().putStringSet("sleep_presets", presets).apply()
        publishState()
    }

    fun cancelSleepTimer() = setSleepTimer(0)

    fun setReplayGainEnabled(enabled: Boolean) {
        playbackPreferences.edit().putBoolean("replay_gain_enabled", enabled).apply()
        controller?.let(::applyReplayGain)
        publishState()
    }

    fun setTransitionFade(enabled: Boolean, seconds: Int = _state.value.transitionFadeSeconds) {
        playbackPreferences.edit()
            .putBoolean("transition_fade_enabled", enabled)
            .putInt("transition_fade_seconds", seconds.coerceIn(1, 12))
            .apply()
        publishState()
    }

    private fun applyReplayGain(player: Player) {
        val enabled = playbackPreferences.getBoolean("replay_gain_enabled", false)
        val extras = player.mediaMetadata.extras
        val gain = if (extras?.containsKey("cathode_replay_gain") == true) extras.getFloat("cathode_replay_gain") else null
        player.volume = if (enabled && gain != null) Math.pow(10.0, gain.toDouble() / 20.0).toFloat().coerceIn(0f, 1f) else 1f
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        audioPreferences.edit().putBoolean("enabled", enabled).apply()
        publishAudioLab()
    }

    fun setBandLevel(band: Int, level: Short) {
        equalizer?.let { effect ->
            if (band in 0 until effect.numberOfBands.toInt()) {
                val applied = level.coerceIn(effect.bandLevelRange[0], effect.bandLevelRange[1])
                effect.setBandLevel(band.toShort(), applied)
                audioPreferences.edit().putInt("band_" + band, applied.toInt()).apply()
                publishAudioLab()
            }
        }
    }

    fun useEqualizerPreset(preset: Int) {
        equalizer?.let { effect ->
            if (preset in 0 until effect.numberOfPresets.toInt()) {
                effect.usePreset(preset.toShort())
                val editor = audioPreferences.edit()
                for (band in 0 until effect.numberOfBands.toInt()) {
                    editor.putInt("band_" + band, effect.getBandLevel(band.toShort()).toInt())
                }
                editor.apply()
                publishAudioLab()
            }
        }
    }

    fun setBassBoost(strength: Short) {
        val applied = strength.coerceIn(0, 1000)
        bassBoost?.setStrength(applied)
        audioPreferences.edit().putInt("bass", applied.toInt()).apply()
        publishAudioLab()
    }

    fun refreshPosition() = publishState()

    private fun publishState() {
        controller?.let { p ->
            val metadata = p.mediaMetadata
            val queue = (0 until p.mediaItemCount).map { index ->
                val item = p.getMediaItemAt(index)
                QueueEntry(
                    mediaId = item.mediaId,
                    title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown title" },
                    artist = item.mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown artist" },
                    artworkUri = item.mediaMetadata.artworkUri,
                    mimeType = item.localConfiguration?.mimeType,
                    sourceUri = item.localConfiguration?.uri,
                )
            }
            _state.value = PlaybackState(
                connected = true,
                isPlaying = p.isPlaying,
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                artworkUri = metadata.artworkUri,
                positionMs = p.currentPosition.coerceAtLeast(0),
                durationMs = p.duration.takeIf { it > 0 } ?: 0,
                isSeekable = p.isCurrentMediaItemSeekable,
                mediaItemIndex = p.currentMediaItemIndex,
                repeatMode = p.repeatMode,
                shuffleEnabled = p.shuffleModeEnabled,
                queue = queue,
                sleepTimerEndEpochMs = playbackPreferences.getLong("sleep_end", 0L)
                    .takeIf { it > System.currentTimeMillis() } ?: 0L,
                sleepTimerPresetsSeconds = playbackPreferences.getStringSet("sleep_presets", emptySet()).orEmpty()
                    .mapNotNull(String::toLongOrNull).filter { it > 0 }.sorted(),
                playbackError = playbackError,
                lyrics = metadata.extras?.getString("cathode_lyrics").orEmpty(),
                replayGainDb = metadata.extras?.takeIf { it.containsKey("cathode_replay_gain") }?.getFloat("cathode_replay_gain"),
                replayGainEnabled = playbackPreferences.getBoolean("replay_gain_enabled", false),
                transitionFadeEnabled = playbackPreferences.getBoolean("transition_fade_enabled", false),
                transitionFadeSeconds = playbackPreferences.getInt("transition_fade_seconds", 3).coerceIn(1, 12),
                audioQuality = runCatching {
                    AudioQuality.valueOf(metadata.extras?.getString("cathode_audio_quality").orEmpty())
                }.getOrDefault(AudioQuality.UNKNOWN),
            )
        }
    }

    override fun onEvents(player: Player, events: Player.Events) = publishState()
    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        playbackError = error.message ?: "This track could not be played."
        CathodeDiagnostics.record(appContext, "Playback", playbackError.orEmpty(), error)
        publishState()
    }
    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        playbackError = null
        controller?.let(::applyReplayGain)
        publishState()
    }
    override fun onAudioSessionIdChanged(audioSessionId: Int) { attachAudioLab(audioSessionId) }

    private fun attachAudioLab(audioSessionId: Int) {
        equalizer?.release()
        bassBoost?.release()
        equalizer = null
        bassBoost = null
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) {
            _audioLab.value = AudioLabState()
            return
        }
        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply {
                for (band in 0 until numberOfBands.toInt()) {
                    val saved = audioPreferences.getInt("band_" + band, getBandLevel(band.toShort()).toInt()).toShort()
                    setBandLevel(band.toShort(), saved.coerceIn(bandLevelRange[0], bandLevelRange[1]))
                }
                enabled = audioPreferences.getBoolean("enabled", false)
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                setStrength(audioPreferences.getInt("bass", 0).toShort().coerceIn(0, 1000))
                enabled = audioPreferences.getBoolean("enabled", false)
            }
            publishAudioLab()
        }.onFailure { _audioLab.value = AudioLabState() }
    }

    private fun publishAudioLab() {
        val effect = equalizer ?: run {
            _audioLab.value = AudioLabState()
            return
        }
        _audioLab.value = AudioLabState(
            available = true,
            enabled = effect.enabled,
            bandLevels = (0 until effect.numberOfBands.toInt()).map { effect.getBandLevel(it.toShort()) },
            centerFrequenciesHz = (0 until effect.numberOfBands.toInt()).map { effect.getCenterFreq(it.toShort()) / 1000 },
            minimumLevel = effect.bandLevelRange[0],
            maximumLevel = effect.bandLevelRange[1],
            presets = (0 until effect.numberOfPresets.toInt()).map { effect.getPresetName(it.toShort()) },
            bassBoost = bassBoost?.roundedStrength ?: 0,
        )
    }

    fun release() {
        controller?.removeListener(this)
        equalizer?.release()
        bassBoost?.release()
        equalizer = null
        bassBoost = null
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }
}
