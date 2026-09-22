package com.twelvepts.cathode.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.twelvepts.cathode.model.AudioTrack
import com.twelvepts.cathode.model.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioLibraryRepository(private val context: Context) {
    private val metadata = context.getSharedPreferences("cathode_metadata", Context.MODE_PRIVATE)

    suspend fun loadTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.Audio.Media.RELATIVE_PATH
        }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (${MediaStore.Audio.Media.DURATION} > 10000 OR ${MediaStore.Audio.Media.DURATION} = 0)"
        val result = mutableListOf<AudioTrack>()

        context.contentResolver.query(
            collection,
            projection.toTypedArray(),
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext()) {
                runCatching {
                val id = cursor.getLong(idColumn)
                val legacyKey = id.toString()
                val displayName = cursor.getString(displayNameColumn).orUnknown("track-$id")
                val fileSize = cursor.getLong(sizeColumn).coerceAtLeast(0)
                val relativePath = if (pathColumn >= 0) cursor.getString(pathColumn) else null
                val key = com.twelvepts.cathode.model.stableTrackKey(relativePath, displayName, fileSize)
                val sourceTitle = cursor.getString(titleColumn).orUnknown(displayName.substringBeforeLast('.').ifBlank { "Unknown track" })
                val sourceArtist = cursor.getString(artistColumn).orUnknown("Unknown artist")
                val sourceAlbum = cursor.getString(albumColumn).orUnknown("Unknown album")
                val uri = ContentUris.withAppendedId(collection, id)
                val flac = if (displayName.endsWith(".flac", true) || cursor.getString(mimeColumn)?.contains("flac", true) == true) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use(::readFlacMetadata) }.getOrNull()
                } else null
                val mime = cursor.getString(mimeColumn).orEmpty().lowercase()
                val quality = when {
                    flac != null && ((flac.sampleRateHz ?: 0) > 48_000 || (flac.bitDepth ?: 0) > 16) -> AudioQuality.HI_RES
                    flac != null -> AudioQuality.LOSSLESS
                    mime.contains("alac") || mime.contains("wav") || mime.contains("aiff") -> AudioQuality.LOSSLESS
                    mime.contains("mpeg") || mime.contains("mp3") || mime.contains("aac") || mime.contains("opus") || mime.contains("vorbis") -> AudioQuality.LOSSY
                    else -> AudioQuality.UNKNOWN
                }
                val spectralAnalyzed = metadata.getBoolean("$key.spectral.analyzed", false)
                val spectralSuspected = metadata.getBoolean("$key.spectral.suspected", false)
                val spectralCutoff = metadata.getInt("$key.spectral.cutoff", -1).takeIf { it > 0 }
                result += AudioTrack(
                    id = id,
                    uri = uri,
                    title = metadata.getString("$key.title", metadata.getString("$legacyKey.title", sourceTitle)).orUnknown(sourceTitle),
                    artist = metadata.getString("$key.artist", metadata.getString("$legacyKey.artist", sourceArtist)).orUnknown(sourceArtist),
                    album = metadata.getString("$key.album", metadata.getString("$legacyKey.album", sourceAlbum)).orUnknown(sourceAlbum),
                    albumId = cursor.getLong(albumIdColumn),
                    durationMs = cursor.getLong(durationColumn).coerceAtLeast(0),
                    trackNumber = cursor.getInt(trackColumn).coerceAtLeast(0) % 1000,
                    year = cursor.getInt(yearColumn).coerceAtLeast(0),
                    mimeType = cursor.getString(mimeColumn),
                    relativePath = relativePath,
                    displayName = displayName,
                    fileSize = fileSize,
                    dateAddedSeconds = cursor.getLong(dateAddedColumn).coerceAtLeast(0),
                    tags = metadata.getString("$key.tags", metadata.getString("$legacyKey.tags", "")).orEmpty(),
                    customArtworkUri = metadata.getString("$key.artwork", metadata.getString("$legacyKey.artwork", null)),
                    lyrics = metadata.getString("$key.lyrics", metadata.getString("$legacyKey.lyrics", "")).orEmpty(),
                    replayGainDb = flac?.replayGainDb,
                    hasFlacSeekTable = flac?.hasSeekTable,
                    sampleRateHz = flac?.sampleRateHz,
                    bitDepth = flac?.bitDepth,
                    audioQuality = if (spectralSuspected && quality in listOf(AudioQuality.LOSSLESS, AudioQuality.HI_RES)) AudioQuality.SUSPECTED_TRANSCODE else quality,
                    spectralAnalyzed = spectralAnalyzed,
                    estimatedCutoffHz = spectralCutoff,
                )
                }
            }
        }
        result
    }

    fun updateMetadata(
        track: AudioTrack,
        title: String,
        artist: String,
        album: String,
        tags: String,
        customArtworkUri: String?,
        lyrics: String,
    ): AudioTrack {
        val cleanTitle = title.trim().ifEmpty { track.title }
        val cleanArtist = artist.trim().ifEmpty { track.artist }
        val cleanAlbum = album.trim().ifEmpty { track.album }
        val cleanTags = tags.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
            .joinToString(", ")

        metadata.edit().apply {
            putString("${track.stableKey}.title", cleanTitle)
            putString("${track.stableKey}.artist", cleanArtist)
            putString("${track.stableKey}.album", cleanAlbum)
            putString("${track.stableKey}.tags", cleanTags)
            if (customArtworkUri.isNullOrBlank()) remove("${track.stableKey}.artwork")
            else putString("${track.stableKey}.artwork", customArtworkUri)
            putString("${track.stableKey}.lyrics", lyrics.trim())
        }.apply()

        return track.copy(
            title = cleanTitle,
            artist = cleanArtist,
            album = cleanAlbum,
            tags = cleanTags,
            customArtworkUri = customArtworkUri?.takeIf(String::isNotBlank),
            lyrics = lyrics.trim(),
        )
    }

    fun clearMetadata(track: AudioTrack) {
        metadata.edit().apply {
            listOf("title", "artist", "album", "tags", "artwork", "lyrics").forEach { field ->
                remove("${track.stableKey}.$field")
                remove("${track.id}.$field")
            }
        }.apply()
    }

    fun analyzeLossless(track: AudioTrack): AudioTrack {
        if (track.audioQuality !in listOf(AudioQuality.LOSSLESS, AudioQuality.HI_RES, AudioQuality.SUSPECTED_TRANSCODE)) return track
        val result = LosslessAnalyzer.analyze(context, track.uri) ?: return track
        metadata.edit()
            .putBoolean("${track.stableKey}.spectral.analyzed", true)
            .putBoolean("${track.stableKey}.spectral.suspected", result.suspectedTranscode)
            .apply { result.estimatedCutoffHz?.let { putInt("${track.stableKey}.spectral.cutoff", it) } }
            .apply()
        val codecQuality = if ((track.sampleRateHz ?: 0) > 48_000 || (track.bitDepth ?: 0) > 16) AudioQuality.HI_RES else AudioQuality.LOSSLESS
        return track.copy(
            audioQuality = if (result.suspectedTranscode) AudioQuality.SUSPECTED_TRANSCODE else codecQuality,
            spectralAnalyzed = true,
            estimatedCutoffHz = result.estimatedCutoffHz,
        )
    }

    private fun String?.orUnknown(fallback: String): String =
        if (isNullOrBlank() || this == "<unknown>") fallback else this
}
