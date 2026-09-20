package com.twelvepts.cathode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twelvepts.cathode.LibraryState
import com.twelvepts.cathode.model.AudioTrack

@Composable
fun HomeScreen(state: LibraryState, onRescan: () -> Unit, onPlay: (AudioTrack) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CathodeHeader("AUDIO TERMINAL", "LOCAL SYSTEM // ${state.tracks.size} TRACKS") }
        item {
            StatusPanel(
                monochromeCount = state.tracks.count(AudioTrack::isMonochromeDownload),
                totalCount = state.tracks.size,
                loading = state.loading,
                onRescan = onRescan,
            )
        }
        item { SectionLabel("RECENT SIGNALS") }
        if (state.tracks.isEmpty()) {
            item { EmptyLibrary(state.permissionGranted) }
        } else {
            items(state.tracks.takeLast(12).reversed(), key = AudioTrack::id) { TrackRow(it, onPlay) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    requestPermission: () -> Unit,
    onRescan: () -> Unit,
    onPlay: (AudioTrack) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        CathodeHeader("LIBRARY", "INDEXED LOCAL AUDIO")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onRescan, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, "Rescan", tint = CathodeCyan)
            }
        }
        when {
            !state.permissionGranted -> PermissionPanel(requestPermission)
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CathodeCyan)
            }
            state.error != null -> TerminalMessage("SCAN ERROR", state.error, CathodeError)
            state.tracks.isEmpty() -> EmptyLibrary(true)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.tracks, key = AudioTrack::id) { TrackRow(it, onPlay) }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
fun SearchScreen(tracks: List<AudioTrack>, onPlay: (AudioTrack) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, tracks) {
        if (query.isBlank()) emptyList() else tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }.take(100)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        CathodeHeader("SEARCH", "QUERY LOCAL INDEX")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("TRACK / ARTIST / ALBUM") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CathodeCyan,
                focusedTextColor = CathodeText,
                cursorColor = CathodeCyan,
                unfocusedBorderColor = CathodeDim,
                unfocusedTextColor = CathodeText,
            ),
        )
        Spacer(Modifier.height(12.dp))
        if (query.isBlank()) {
            TerminalMessage("AWAITING INPUT", "> enter search parameters", CathodeMuted)
        } else if (results.isEmpty()) {
            TerminalMessage("NO SIGNAL", "> no local matches", CathodeMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results, key = AudioTrack::id) { TrackRow(it, onPlay) }
            }
        }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, onPlay: (AudioTrack) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = "${track.album} cover",
            modifier = Modifier.size(52.dp).background(CathodePanel).border(1.dp, CathodeDim),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${track.artist} // ${track.album}",
                color = CathodeMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatDuration(track.durationMs), color = CathodeDim, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusPanel(monochromeCount: Int, totalCount: Int, loading: Boolean, onRescan: () -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, CathodeDim).background(CathodePanel).padding(16.dp)) {
        Text("SYSTEM STATUS", color = CathodeCyan, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("> MEDIASTORE: ${if (loading) "SCANNING" else "ONLINE"}")
        Text("> LOCAL TRACKS: $totalCount")
        Text("> MONOCHROME: $monochromeCount")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRescan, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = CathodeCyan)) {
            Text(if (loading) "SCANNING..." else "RESCAN STORAGE", color = CathodeBlack)
        }
    }
}

@Composable
private fun PermissionPanel(onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, CathodeError).padding(18.dp)) {
        Text("STORAGE LINK OFFLINE", color = CathodeError, fontWeight = FontWeight.Bold)
        Text("Cathode needs audio access to index and play local files.", modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRequest) { Text("GRANT AUDIO ACCESS") }
    }
}

@Composable
private fun EmptyLibrary(hasPermission: Boolean) {
    TerminalMessage(
        if (hasPermission) "NO AUDIO INDEXED" else "ACCESS REQUIRED",
        if (hasPermission) "> Download audio in ACQUIRE, then run RESCAN STORAGE.\n> Expected path: Downloads/Monochrome" else "> grant audio access to initialize library",
        CathodeMuted,
    )
}

@Composable
private fun CathodeHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 22.dp, bottom = 14.dp)) {
        Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text("[ $label ]", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun TerminalMessage(title: String, body: String, color: Color) {
    Column(Modifier.fillMaxWidth().border(1.dp, CathodeDim).padding(18.dp)) {
        Text(title, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = CathodeMuted)
    }
}

fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
