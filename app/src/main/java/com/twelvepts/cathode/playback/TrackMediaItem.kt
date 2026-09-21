package com.twelvepts.cathode.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.twelvepts.cathode.model.AudioTrack

fun AudioTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(stableKey)
    .setUri(uri)
    .setMimeType(mimeType)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri)
            .setTrackNumber(trackNumber.takeIf { it > 0 })
            .setReleaseYear(year.takeIf { it > 0 })
            .setExtras(Bundle().apply {
                putString("cathode_lyrics", lyrics)
                replayGainDb?.let { putFloat("cathode_replay_gain", it) }
                hasFlacSeekTable?.let { putBoolean("cathode_flac_seek_table", it) }
            })
            .setIsPlayable(true)
            .build(),
    )
    .build()
