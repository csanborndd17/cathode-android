package com.twelvepts.cathode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twelvepts.cathode.data.AudioLibraryRepository
import com.twelvepts.cathode.data.CathodeLibraryDatabase
import com.twelvepts.cathode.data.PlaylistSummary
import com.twelvepts.cathode.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryState(
    val loading: Boolean = false,
    val permissionGranted: Boolean = false,
    val tracks: List<AudioTrack> = emptyList(),
    val error: String? = null,
    val favoriteKeys: Set<String> = emptySet(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val playlistTrackKeys: Map<Long, List<String>> = emptyMap(),
    val recentTrackKeys: List<String> = emptyList(),
    val playCounts: Map<String, Int> = emptyMap(),
)

class CathodeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioLibraryRepository(application)
    private val database = CathodeLibraryDatabase(application)
    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

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
                    onFailure = { LibraryState(false, true, error = it.message ?: "Library scan failed") },
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
            recentTrackKeys = database.recentTrackKeys(),
            playCounts = database.playCounts(),
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

    fun deletePlaylist(id: Long) {
        database.deletePlaylist(id)
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

    fun recordPlay(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            database.recordPlay(track.stableKey)
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
    ) {
        val updated = repository.updateMetadata(track, title, artist, album, tags, customArtworkUri)
        _library.value = _library.value.copy(
            tracks = _library.value.tracks.map { if (it.id == updated.id) updated else it },
        )
    }
}
