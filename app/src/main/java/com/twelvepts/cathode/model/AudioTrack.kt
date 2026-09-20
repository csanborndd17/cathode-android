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
) {
    val artworkUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")

    val isMonochromeDownload: Boolean
        get() = relativePath?.contains("Monochrome", ignoreCase = true) == true
}
