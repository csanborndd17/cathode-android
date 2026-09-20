package com.twelvepts.cathode.model

import android.net.Uri

data class AudioTrack(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val mimeType: String?,
    val relativePath: String?,
    val tags: String = "",
    val customArtworkUri: String? = null,
) {
    val artworkUri: Uri
        get() = customArtworkUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: Uri.parse("content://media/external/audio/albumart/$albumId")

    val isMonochromeDownload: Boolean
        get() = relativePath?.contains("Monochrome", ignoreCase = true) == true
}
