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
    val displayName: String,
    val fileSize: Long,
    val dateAddedSeconds: Long,
    val tags: String = "",
    val customArtworkUri: String? = null,
) {
    val stableKey: String
        get() = stableTrackKey(relativePath, displayName, fileSize)

    val artworkUri: Uri
        get() = customArtworkUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: Uri.parse("content://media/external/audio/albumart/$albumId")

    val isMonochromeDownload: Boolean
        get() = relativePath?.contains("Monochrome", ignoreCase = true) == true
}


fun stableTrackKey(relativePath: String?, displayName: String, fileSize: Long): String {
    val source = "${relativePath.orEmpty().trim().lowercase()}|${displayName.trim().lowercase()}|$fileSize"
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
