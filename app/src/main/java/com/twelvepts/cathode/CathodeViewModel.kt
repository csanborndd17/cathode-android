package com.twelvepts.cathode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twelvepts.cathode.data.AudioLibraryRepository
import com.twelvepts.cathode.data.CathodeLibraryDatabase
import com.twelvepts.cathode.data.PlaylistSummary
import com.twelvepts.cathode.data.SmartPlaylist
import com.twelvepts.cathode.data.TransmissionYear
import com.twelvepts.cathode.data.CathodeDiagnostics
import com.twelvepts.cathode.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class LibraryState(
    val loading: Boolean = false,
    val permissionGranted: Boolean = false,
    val tracks: List<AudioTrack> = emptyList(),
    val error: String? = null,
    val favoriteKeys: Set<String> = emptySet(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val playlistTrackKeys: Map<Long, List<String>> = emptyMap(),
    val smartPlaylists: List<SmartPlaylist> = emptyList(),
    val recentTrackKeys: List<String> = emptyList(),
    val playCounts: Map<String, Int> = emptyMap(),
    val transmissionYears: Map<Int, TransmissionYear> = emptyMap(),
    val analysisRunning: Boolean = false,
    val analysisCompleted: Int = 0,
    val analysisTotal: Int = 0,
)

class CathodeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioLibraryRepository(application)
    private val database = CathodeLibraryDatabase(application)
    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()
    private var analysisJob: Job? = null

    fun setPermission(granted: Boolean) {
        _library.value = _library.value.copy(permissionGranted = granted)
        if (granted) rescan()
    }

    fun rescan() {
        if (!_library.value.permissionGranted || _library.value.loading) return
        viewModelScope.launch {
            _library.value = _library.value.copy(loading = true, error = null)
            _library.value = runCatching { repository.loadTracks() }
                .fold(
                    onSuccess = { tracks -> libraryState(tracks) },
                    onFailure = {
                        CathodeDiagnostics.record(getApplication(), "Library", "Media scan failed", it)
                        LibraryState(false, true, error = it.message ?: "Library scan failed")
                    },
                )
        }
    }

    private fun libraryState(tracks: List<AudioTrack>, loading: Boolean = false): LibraryState {
        val playlists = database.playlists()
        return LibraryState(
            loading = loading,
            permissionGranted = true,
            tracks = tracks,
            favoriteKeys = database.favoriteKeys(),
            playlists = playlists,
            playlistTrackKeys = playlists.associate { it.id to database.playlistTrackKeys(it.id) },
            smartPlaylists = database.smartPlaylists(),
            recentTrackKeys = database.recentTrackKeys(),
            playCounts = database.playCounts(),
            transmissionYears = database.transmissionYears(),
        )
    }

    private fun refreshCollections() {
        _library.value = libraryState(_library.value.tracks)
    }

    fun toggleFavorite(track: AudioTrack) {
        database.toggleFavorite(track.stableKey)
        refreshCollections()
    }

    fun createPlaylist(name: String) {
        database.createPlaylist(name)
        refreshCollections()
    }

    fun createPlaylistWithTracks(name: String, tracks: List<AudioTrack>) {
        val playlistId = database.createPlaylist(name)
        tracks.forEach { database.addToPlaylist(playlistId, it.stableKey) }
        refreshCollections()
    }

    fun addTracksToPlaylist(playlistId: Long, tracks: List<AudioTrack>) {
        tracks.forEach { database.addToPlaylist(playlistId, it.stableKey) }
        refreshCollections()
    }

    fun deletePlaylist(id: Long) {
        database.deletePlaylist(id)
        refreshCollections()
    }

    fun updatePlaylist(id: Long, name: String, artworkUri: String?) {
        database.updatePlaylist(id, name, artworkUri)
        refreshCollections()
    }

    fun addToPlaylist(playlistId: Long, track: AudioTrack) {
        database.addToPlaylist(playlistId, track.stableKey)
        refreshCollections()
    }

    fun removeFromPlaylist(playlistId: Long, track: AudioTrack) {
        database.removeFromPlaylist(playlistId, track.stableKey)
        refreshCollections()
    }

    fun movePlaylistTrack(playlistId: Long, track: AudioTrack, direction: Int) {
        database.movePlaylistTrack(playlistId, track.stableKey, direction)
        refreshCollections()
    }

    fun createSmartPlaylist(name: String, rule: String, value: String) {
        database.createSmartPlaylist(name, rule, value)
        refreshCollections()
    }

    fun updateSmartPlaylist(id: Long, name: String, rule: String, value: String) {
        database.updateSmartPlaylist(id, name, rule, value)
        refreshCollections()
    }

    fun deleteSmartPlaylist(id: Long) {
        database.deleteSmartPlaylist(id)
        refreshCollections()
    }

    fun recordPlaybackStart(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            database.recordPlaybackStart(track.stableKey)
            refreshCollections()
        }
    }

    fun recordListening(track: AudioTrack, listenedMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.recordListening(track.stableKey, listenedMs)
            refreshCollections()
        }
    }

    fun resetMetadata(track: AudioTrack) {
        repository.clearMetadata(track)
        rescan()
    }

    fun updateMetadata(
        track: AudioTrack,
        title: String,
        artist: String,
        album: String,
        tags: String,
        customArtworkUri: String?,
        lyrics: String,
    ) {
        val updated = repository.updateMetadata(track, title, artist, album, tags, customArtworkUri, lyrics)
        _library.value = _library.value.copy(
            tracks = _library.value.tracks.map { if (it.id == updated.id) updated else it },
        )
    }

    fun analyzeLosslessLibrary() = analyzeLosslessLibrary(force = false)

    fun reanalyzeLosslessLibrary() = analyzeLosslessLibrary(force = true)

    private fun analyzeLosslessLibrary(force: Boolean) {
        if (analysisJob?.isActive == true) return
        if (force) repository.clearLosslessAnalysis(_library.value.tracks)
        val candidates = _library.value.tracks.filter {
            it.audioQuality in listOf(com.twelvepts.cathode.model.AudioQuality.LOSSLESS, com.twelvepts.cathode.model.AudioQuality.HI_RES, com.twelvepts.cathode.model.AudioQuality.SUSPECTED_TRANSCODE) && (force || !it.spectralAnalyzed)
        }
        analysisJob = viewModelScope.launch(Dispatchers.IO) {
            _library.value = _library.value.copy(analysisRunning = true, analysisCompleted = 0, analysisTotal = candidates.size)
            candidates.forEachIndexed { index, track ->
                val updated = repository.analyzeLossless(track)
                _library.value = _library.value.copy(
                    tracks = _library.value.tracks.map { if (it.stableKey == updated.stableKey) updated else it },
                    analysisCompleted = index + 1,
                )
            }
            _library.value = _library.value.copy(analysisRunning = false)
        }
    }

    fun cancelLosslessAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        _library.value = _library.value.copy(analysisRunning = false)
    }
}
