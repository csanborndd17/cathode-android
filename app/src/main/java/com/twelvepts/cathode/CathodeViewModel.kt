package com.twelvepts.cathode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twelvepts.cathode.data.AudioLibraryRepository
import com.twelvepts.cathode.model.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryState(
    val loading: Boolean = false,
    val permissionGranted: Boolean = false,
    val tracks: List<AudioTrack> = emptyList(),
    val error: String? = null,
)

class CathodeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioLibraryRepository(application)
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
                    onSuccess = { LibraryState(false, true, it) },
                    onFailure = { LibraryState(false, true, error = it.message ?: "Library scan failed") },
                )
        }
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
