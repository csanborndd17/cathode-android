package com.twelvepts.cathode.data

import android.net.Uri
import com.twelvepts.cathode.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LyricsLookupResult(val lyrics: String, val synchronized: Boolean)

object LyricsRepository {
    suspend fun find(track: AudioTrack): Result<LyricsLookupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = buildList {
                add("track_name=${Uri.encode(track.title)}")
                add("artist_name=${Uri.encode(track.artist)}")
                if (track.album.isNotBlank() && track.album != "Unknown album") add("album_name=${Uri.encode(track.album)}")
                if (track.durationMs > 0) add("duration=${track.durationMs / 1000}")
            }.joinToString("&")
            val connection = URL("https://lrclib.net/api/get?$query").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Cathode/0.11.1 (com.twelvepts.cathode)")
                when (connection.responseCode) {
                    200 -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val record = JSONObject(body)
                        val synced = record.optString("syncedLyrics").trim()
                        val plain = record.optString("plainLyrics").trim()
                        when {
                            synced.isNotBlank() -> LyricsLookupResult(synced, true)
                            plain.isNotBlank() -> LyricsLookupResult(plain, false)
                            else -> error("The matching record has no lyrics.")
                        }
                    }
                    404 -> error("No lyrics were found for this track.")
                    else -> error("Lyrics service returned ${connection.responseCode}.")
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
