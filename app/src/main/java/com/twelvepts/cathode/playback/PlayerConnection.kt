package com.twelvepts.cathode.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.twelvepts.cathode.model.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueEntry(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
)

data class PlaybackState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artworkUri: Uri? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val mediaItemIndex: Int = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
)

class PlayerConnection(context: Context) : Player.Listener {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null

    init {
        val token = SessionToken(appContext, ComponentName(appContext, CathodePlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }.getOrNull()?.let {
                controller = it
                it.addListener(this)
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
        if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount && from != to) {
            it.moveMediaItem(from, to)
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
                mediaItemIndex = p.currentMediaItemIndex,
                repeatMode = p.repeatMode,
                shuffleEnabled = p.shuffleModeEnabled,
                queue = queue,
            )
        }
    }

    override fun onEvents(player: Player, events: Player.Events) = publishState()

    fun release() {
        controller?.removeListener(this)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }
}
