package com.twelvepts.cathode.playback

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
            .setIsPlayable(true)
            .build(),
    )
    .build()
