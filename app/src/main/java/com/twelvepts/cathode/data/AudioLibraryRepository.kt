package com.twelvepts.cathode.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.twelvepts.cathode.model.AudioTrack
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
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.Audio.Media.RELATIVE_PATH
        }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
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
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val key = id.toString()
                val sourceTitle = cursor.getString(titleColumn).orUnknown("Unknown track")
                val sourceArtist = cursor.getString(artistColumn).orUnknown("Unknown artist")
                val sourceAlbum = cursor.getString(albumColumn).orUnknown("Unknown album")
                result += AudioTrack(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = metadata.getString("$key.title", sourceTitle).orUnknown(sourceTitle),
                    artist = metadata.getString("$key.artist", sourceArtist).orUnknown(sourceArtist),
                    album = metadata.getString("$key.album", sourceAlbum).orUnknown(sourceAlbum),
                    albumId = cursor.getLong(albumIdColumn),
                    durationMs = cursor.getLong(durationColumn),
                    trackNumber = cursor.getInt(trackColumn) % 1000,
                    year = cursor.getInt(yearColumn),
                    mimeType = cursor.getString(mimeColumn),
                    relativePath = if (pathColumn >= 0) cursor.getString(pathColumn) else null,
                    tags = metadata.getString("$key.tags", "").orEmpty(),
                    customArtworkUri = metadata.getString("$key.artwork", null),
                )
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
            putString("${track.id}.title", cleanTitle)
            putString("${track.id}.artist", cleanArtist)
            putString("${track.id}.album", cleanAlbum)
            putString("${track.id}.tags", cleanTags)
            if (customArtworkUri.isNullOrBlank()) remove("${track.id}.artwork")
            else putString("${track.id}.artwork", customArtworkUri)
        }.apply()

        return track.copy(
            title = cleanTitle,
            artist = cleanArtist,
            album = cleanAlbum,
            tags = cleanTags,
            customArtworkUri = customArtworkUri?.takeIf(String::isNotBlank),
        )
    }

    private fun String?.orUnknown(fallback: String): String =
        if (isNullOrBlank() || this == "<unknown>") fallback else this
}
