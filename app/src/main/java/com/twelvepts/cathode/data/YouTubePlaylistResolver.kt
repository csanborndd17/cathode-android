package com.twelvepts.cathode.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

data class YouTubeTrack(val artist: String, val title: String)

class YouTubePlaylistResolver(context: Context) {
    private val preferences = context.getSharedPreferences("youtube_connection", Context.MODE_PRIVATE)
    private val secure = SecureStore(context, "youtube_secure", "cathode_youtube_credentials")

    init {
        preferences.getString("api_key", null)?.let { old ->
            if (secure.getString("api_key") == null) secure.putString("api_key", old)
            preferences.edit().remove("api_key").apply()
        }
    }

    var apiKey: String
        get() = secure.getString("api_key").orEmpty()
        set(value) { secure.putString("api_key", value.trim()) }

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    fun clear() { secure.remove("api_key") }

    suspend fun resolve(playlistUrl: String): Result<List<YouTubeTrack>> = runCatching {
        require(apiKey.isNotBlank()) { "Add a YouTube Data API key in Settings first." }
        val playlistId = extractPlaylistId(playlistUrl) ?: error("That link does not contain a YouTube playlist ID.")
        val tracks = mutableListOf<YouTubeTrack>()
        var pageToken: String? = null
        do {
            val request = Uri.parse("https://www.googleapis.com/youtube/v3/playlistItems").buildUpon()
                .appendQueryParameter("part", "snippet")
                .appendQueryParameter("maxResults", "50")
                .appendQueryParameter("playlistId", playlistId)
                .appendQueryParameter("key", apiKey)
                .apply { pageToken?.let { appendQueryParameter("pageToken", it) } }
                .build().toString()
            val json = requestJson(request)
            val items = json.optJSONArray("items") ?: error("YouTube returned no playlist entries.")
            for (index in 0 until items.length()) {
                val snippet = items.optJSONObject(index)?.optJSONObject("snippet") ?: continue
                val rawTitle = snippet.optString("title").trim()
                if (rawTitle.isBlank() || rawTitle == "Deleted video" || rawTitle == "Private video") continue
                tracks += parseVideoTitle(rawTitle, snippet.optString("videoOwnerChannelTitle"))
            }
            pageToken = json.optString("nextPageToken").takeIf(String::isNotBlank)
        } while (pageToken != null)
        tracks.distinctBy { "${it.artist}\u0000${it.title}".lowercase() }
    }

    private suspend fun requestJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        val text = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        require(connection.responseCode in 200..299) {
            val reason = runCatching {
                JSONObject(text).getJSONObject("error").optString("message")
            }.getOrNull()
            reason?.takeIf(String::isNotBlank) ?: "YouTube playlist request failed (${connection.responseCode})."
        }
        JSONObject(text)
    }

    companion object {
        fun extractPlaylistId(url: String): String? = Regex("[?&]list=([^&#]+)")
            .find(url)?.groupValues?.getOrNull(1)
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf(String::isNotBlank)

        fun parseVideoTitle(rawTitle: String, channelTitle: String = ""): YouTubeTrack {
            val cleaned = rawTitle
                .replace(Regex("\\s*[\\[(](official\\s+)?(music\\s+)?(video|audio|lyrics?|visuali[sz]er)[^\\])]*[\\])]\\s*$", RegexOption.IGNORE_CASE), "")
                .trim()
            val separator = listOf(" — ", " – ", " - ", " | ").firstOrNull(cleaned::contains)
            if (separator != null) {
                val parts = cleaned.split(separator, limit = 2)
                return YouTubeTrack(parts[0].trim(), parts[1].trim())
            }
            val channel = channelTitle.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "").trim()
            return YouTubeTrack(channel, cleaned)
        }
    }
}
