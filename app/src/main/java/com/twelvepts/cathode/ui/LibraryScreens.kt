package com.twelvepts.cathode.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twelvepts.cathode.LibraryState
import com.twelvepts.cathode.model.AudioTrack

typealias MetadataEditor = (AudioTrack, String, String, String, String) -> Unit

@Composable
fun HomeScreen(
    state: LibraryState,
    onRescan: () -> Unit,
    onPlay: (AudioTrack) -> Unit,
    onEdit: MetadataEditor,
) {
    val albumCount = remember(state.tracks) { state.tracks.map(AudioTrack::album).distinct().size }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Your music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${state.tracks.size} songs · $albumCount albums · available offline", color = CathodeMuted)
                }
                IconButton(onClick = onRescan, enabled = !state.loading) {
                    Icon(Icons.Default.Refresh, "Rescan library", tint = CathodeCyan)
                }
            }
        }
        if (state.tracks.isNotEmpty()) {
            item {
                Button(onClick = { onPlay(state.tracks.first()) }) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("Play all", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item { SectionLabel("Recently added") }
        if (state.tracks.isEmpty()) {
            item { EmptyLibrary(state.permissionGranted) }
        } else {
            items(state.tracks.take(12), key = AudioTrack::id) { TrackRow(it, onPlay, onEdit) }
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
    onEdit: MetadataEditor,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("All local audio", color = CathodeMuted)
            }
            IconButton(onClick = onRescan, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, "Rescan", tint = CathodeCyan)
            }
        }
        when {
            !state.permissionGranted -> PermissionPanel(requestPermission)
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CathodeCyan)
            }
            state.error != null -> MessagePanel("Library scan failed", state.error, CathodeError)
            state.tracks.isEmpty() -> EmptyLibrary(true)
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.tracks, key = AudioTrack::id) { TrackRow(it, onPlay, onEdit) }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
fun SearchScreen(
    tracks: List<AudioTrack>,
    onPlay: (AudioTrack) -> Unit,
    onEdit: MetadataEditor,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, tracks) {
        if (query.isBlank()) emptyList() else tracks.filter {
            it.title.contains(query, true) ||
                it.artist.contains(query, true) ||
                it.album.contains(query, true) ||
                it.tags.contains(query, true)
        }.take(100)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Column(Modifier.padding(top = 24.dp, bottom = 14.dp)) {
            Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Titles, artists, albums, and your tags", color = CathodeMuted)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search your library") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CathodeCyan,
                focusedTextColor = CathodeText,
                cursorColor = CathodeCyan,
                unfocusedBorderColor = CathodeDim,
                unfocusedTextColor = CathodeText,
            ),
        )
        Spacer(Modifier.height(12.dp))
        when {
            query.isBlank() -> MessagePanel("Search your music", "Custom tags are searchable too.", CathodeMuted)
            results.isEmpty() -> MessagePanel("No results", "Try another title, artist, album, or tag.", CathodeMuted)
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results, key = AudioTrack::id) { TrackRow(it, onPlay, onEdit) }
            }
        }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, onPlay: (AudioTrack) -> Unit, onEdit: MetadataEditor) {
    var editing by remember(track.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = "${track.album} cover",
            modifier = Modifier.size(52.dp).background(CathodePanel),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${track.artist} · ${track.album}",
                color = CathodeMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.tags.isNotEmpty()) {
                Text(track.tags, color = CathodeDim, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        Text(formatDuration(track.durationMs), color = CathodeDim, style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = { editing = true }) {
            Icon(Icons.Default.Edit, "Edit metadata", tint = CathodeMuted)
        }
    }
    if (editing) {
        MetadataDialog(
            track = track,
            onDismiss = { editing = false },
            onSave = { title, artist, album, tags ->
                onEdit(track, title, artist, album, tags)
                editing = false
            },
        )
    }
}

@Composable
private fun MetadataDialog(
    track: AudioTrack,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var tags by remember(track.id) { mutableStateOf(track.tags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit track details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true)
                OutlinedTextField(
                    tags,
                    { tags = it },
                    label = { Text("Search tags") },
                    supportingText = { Text("Separate tags with commas, e.g. twenty one pilots, demo") },
                )
                Text(
                    "Changes are stored in Cathode and do not rewrite the original audio file.",
                    color = CathodeMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, artist, album, tags) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PermissionPanel(onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(CathodePanel).padding(18.dp)) {
        Text("Audio access needed", color = CathodeError, fontWeight = FontWeight.Bold)
        Text("Allow Cathode to index and play music stored on this phone.", modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRequest) { Text("Allow audio access") }
    }
}

@Composable
private fun EmptyLibrary(hasPermission: Boolean) {
    MessagePanel(
        if (hasPermission) "No music found" else "Audio access needed",
        if (hasPermission) "Download or copy music to this phone, then rescan the library." else "Grant audio access to load your local music.",
        CathodeMuted,
    )
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun MessagePanel(title: String, body: String, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.fillMaxWidth().background(CathodePanel).padding(18.dp)) {
        Text(title, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = CathodeMuted)
    }
}

fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
