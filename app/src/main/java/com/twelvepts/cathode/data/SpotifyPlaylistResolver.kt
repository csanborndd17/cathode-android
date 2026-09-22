package com.twelvepts.cathode.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class SpotifyTrack(val artist: String, val title: String)

class SpotifyPlaylistResolver(private val context: Context) {
    companion object {
        const val REDIRECT_URI = "http://127.0.0.1:43821/callback"
        private const val CALLBACK_PORT = 43821
    }
    private val preferences = context.getSharedPreferences("spotify_connection", Context.MODE_PRIVATE)

    var clientId: String
        get() = preferences.getString("client_id", "").orEmpty()
        set(value) { preferences.edit().putString("client_id", value.trim()).apply() }

    val isConnected: Boolean get() = preferences.getString("refresh_token", null) != null

    fun disconnect() {
        preferences.edit().remove("access_token").remove("refresh_token").remove("expires_at").apply()
    }

    suspend fun connect(): Result<Unit> = runCatching {
        require(clientId.isNotBlank()) { "Enter your Spotify Client ID first." }
        val server = withContext(Dispatchers.IO) {
            ServerSocket(CALLBACK_PORT, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 180_000 }
        }
        val redirectUri = REDIRECT_URI
        val verifier = randomUrlSafe(64)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
        )
        val state = randomUrlSafe(32)
        val authorization = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative")
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, authorization).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        val callback = withContext(Dispatchers.IO) {
            server.use {
                val socket = it.accept()
                socket.use { connection ->
                    val request = BufferedReader(InputStreamReader(connection.getInputStream())).readLine().orEmpty()
                    val target = request.split(' ').getOrNull(1).orEmpty()
                    val callbackUri = Uri.parse("http://127.0.0.1$target")
                    val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n" +
                        "<html><body style='background:#05090b;color:#00e5ff;font-family:sans-serif;padding:32px'><h2>Cathode connected</h2><p>You can return to Cathode.</p></body></html>"
                    connection.getOutputStream().write(response.toByteArray())
                    callbackUri
                }
            }
        }
        require(callback.getQueryParameter("state") == state) { "Spotify authorization state did not match." }
        callback.getQueryParameter("error")?.let { error("Spotify authorization failed: $it") }
        val code = callback.getQueryParameter("code") ?: error("Spotify did not return an authorization code.")
        exchangeToken(code, verifier, redirectUri)
    }

    suspend fun resolve(playlistUrl: String): Result<List<SpotifyTrack>> = runCatching {
        val playlistId = Regex("(?:playlist/|spotify:playlist:)([A-Za-z0-9]+)")
            .find(playlistUrl)?.groupValues?.getOrNull(1) ?: error("That is not a Spotify playlist link.")
        val token = validAccessToken()
        val tracks = mutableListOf<SpotifyTrack>()
        var next: String? = "https://api.spotify.com/v1/playlists/$playlistId/items?limit=50"
        while (next != null) {
            val json = requestJson(next, token)
            val items = json.getJSONArray("items")
            for (index in 0 until items.length()) {
                val row = items.optJSONObject(index) ?: continue
                val track = row.optJSONObject("item") ?: row.optJSONObject("track") ?: continue
                val title = track.optString("name").trim()
                val artistsJson = track.optJSONArray("artists")
                val artists = buildList {
                    if (artistsJson != null) for (artistIndex in 0 until artistsJson.length()) {
                        artistsJson.optJSONObject(artistIndex)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }.joinToString(", ")
                if (title.isNotBlank()) tracks += SpotifyTrack(artists, title)
            }
            next = json.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }
        tracks.distinctBy { "${it.artist}\u0000${it.title}".lowercase() }
    }

    private suspend fun validAccessToken(): String {
        val access = preferences.getString("access_token", null)
        val expiresAt = preferences.getLong("expires_at", 0L)
        if (access != null && System.currentTimeMillis() < expiresAt - 60_000L) return access
        val refresh = preferences.getString("refresh_token", null) ?: error("Connect Spotify in Settings first.")
        val response = postToken(mapOf("client_id" to clientId, "grant_type" to "refresh_token", "refresh_token" to refresh))
        saveTokens(response, refresh)
        return response.getString("access_token")
    }

    private suspend fun exchangeToken(code: String, verifier: String, redirectUri: String) {
        val response = postToken(mapOf(
            "client_id" to clientId,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to verifier,
        ))
        saveTokens(response, null)
    }

    private suspend fun postToken(fields: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val body = fields.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val connection = URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(body.toByteArray()) }
        val text = connection.responseText()
        require(connection.responseCode in 200..299) { JSONObject(text).optString("error_description", "Spotify token request failed (${connection.responseCode}).") }
        JSONObject(text)
    }

    private suspend fun requestJson(url: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        val text = connection.responseText()
        require(connection.responseCode in 200..299) {
            if (connection.responseCode == 403) "Spotify only allows this app to read playlists available to the connected account." else "Spotify playlist request failed (${connection.responseCode})."
        }
        JSONObject(text)
    }

    private fun saveTokens(json: JSONObject, fallbackRefresh: String?) {
        preferences.edit()
            .putString("access_token", json.getString("access_token"))
            .putString("refresh_token", json.optString("refresh_token").takeIf(String::isNotBlank) ?: fallbackRefresh)
            .putLong("expires_at", System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
            .apply()
    }

    private fun HttpURLConnection.responseText(): String =
        (if (responseCode in 200..299) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun randomUrlSafe(bytes: Int): String = ByteArray(bytes).also(SecureRandom()::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}
