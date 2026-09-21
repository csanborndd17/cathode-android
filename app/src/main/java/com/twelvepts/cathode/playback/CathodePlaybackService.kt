package com.twelvepts.cathode.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.twelvepts.cathode.MainActivity
import com.twelvepts.cathode.data.CathodeLibraryDatabase
import org.json.JSONArray
import org.json.JSONObject

class CathodePlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var database: CathodeLibraryDatabase
    private val handler = Handler(Looper.getMainLooper())
    private var lastRecordedMediaId: String? = null

    private val checkpoint = object : Runnable {
        override fun run() {
            val player = mediaSession?.player ?: return
            if (player.isPlaying) {
                player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)?.let {
                    database.recordListening(it, 30_000)
                }
            }
            saveSession(player)
            handler.postDelayed(this, 30_000)
        }
    }
    private val sleepTimer = object : Runnable {
        override fun run() {
            val player = mediaSession?.player ?: return
            val preferences = getSharedPreferences("playback_session", MODE_PRIVATE)
            val end = preferences.getLong("sleep_end", 0L)
            if (end > 0L && System.currentTimeMillis() >= end) {
                player.pause()
                preferences.edit().remove("sleep_end").apply()
                saveSession(player)
            }
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = CathodeLibraryDatabase(this)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            pauseAtEndOfMediaItems = false
        }
        restoreSession(player)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) recordStart(player, false)
                saveSession(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player.isPlaying) recordStart(player, true)
                saveSession(player)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = saveSession(player)
            override fun onRepeatModeChanged(repeatMode: Int) = saveSession(player)
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = saveSession(player)
            override fun onPlayerError(error: PlaybackException) {
                val failedIndex = player.currentMediaItemIndex
                val shouldContinue = player.playWhenReady
                if (failedIndex >= 0 && player.mediaItemCount > 1) {
                    player.removeMediaItem(failedIndex)
                    player.prepare()
                    if (shouldContinue) player.play()
                }
                saveSession(player)
            }
        })

        val activityIntent = Intent(this, MainActivity::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(sessionActivity).build()
        handler.postDelayed(checkpoint, 30_000)
        handler.post(sleepTimer)
    }

    private fun recordStart(player: Player, force: Boolean) {
        val mediaId = player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank) ?: return
        if (force || mediaId != lastRecordedMediaId) {
            database.recordPlaybackStart(mediaId)
            lastRecordedMediaId = mediaId
        }
    }

    private fun saveSession(player: Player) {
        val items = JSONArray()
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            items.put(JSONObject().apply {
                put("id", item.mediaId)
                put("uri", item.localConfiguration?.uri?.toString().orEmpty())
                put("mime", item.localConfiguration?.mimeType.orEmpty())
                put("title", metadata.title?.toString().orEmpty())
                put("artist", metadata.artist?.toString().orEmpty())
                put("album", metadata.albumTitle?.toString().orEmpty())
                put("artwork", metadata.artworkUri?.toString().orEmpty())
            })
        }
        getSharedPreferences("playback_session", MODE_PRIVATE).edit()
            .putString("queue", items.toString())
            .putInt("index", player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("position", player.currentPosition.coerceAtLeast(0))
            .putInt("repeat", player.repeatMode)
            .putBoolean("shuffle", player.shuffleModeEnabled)
            .apply()
    }

    private fun restoreSession(player: ExoPlayer) {
        val preferences = getSharedPreferences("playback_session", MODE_PRIVATE)
        val encoded = preferences.getString("queue", null) ?: return
        val items = runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    val uri = value.optString("uri")
                    if (uri.isBlank()) continue
                    add(
                        MediaItem.Builder()
                            .setMediaId(value.optString("id"))
                            .setUri(Uri.parse(uri))
                            .setMimeType(value.optString("mime").takeIf(String::isNotBlank))
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(value.optString("title"))
                                    .setArtist(value.optString("artist"))
                                    .setAlbumTitle(value.optString("album"))
                                    .setArtworkUri(value.optString("artwork").takeIf(String::isNotBlank)?.let(Uri::parse))
                                    .setIsPlayable(true)
                                    .build(),
                            )
                            .build(),
                    )
                }
            }
        }.getOrDefault(emptyList())
        if (items.isEmpty()) return
        val index = preferences.getInt("index", 0).coerceIn(0, items.lastIndex)
        val position = preferences.getLong("position", 0).coerceAtLeast(0)
        player.setMediaItems(items, index, position)
        player.repeatMode = preferences.getInt("repeat", Player.REPEAT_MODE_OFF)
        player.shuffleModeEnabled = preferences.getBoolean("shuffle", false)
        player.prepare()
        player.pause()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkpoint)
        handler.removeCallbacks(sleepTimer)
        mediaSession?.run {
            saveSession(player)
            player.release()
            release()
        }
        database.close()
        mediaSession = null
        super.onDestroy()
    }
}
