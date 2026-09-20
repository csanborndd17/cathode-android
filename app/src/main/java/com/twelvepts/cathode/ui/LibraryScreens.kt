package com.twelvepts.cathode.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twelvepts.cathode.LibraryState
import com.twelvepts.cathode.data.PlaylistSummary
import com.twelvepts.cathode.model.AudioTrack

typealias MetadataEditor = (AudioTrack, String, String, String, String, String?) -> Unit
typealias MetadataResetter = (AudioTrack) -> Unit
typealias FavoriteToggler = (AudioTrack) -> Unit
typealias PlaylistAdder = (Long, AudioTrack) -> Unit

@Composable
fun HomeScreen(
    state: LibraryState,
    onRescan: () -> Unit,
    onPlay: (AudioTrack) -> Unit,
    onEdit: MetadataEditor,
    onReset: MetadataResetter,
    onToggleFavorite: FavoriteToggler,
    onAddToPlaylist: PlaylistAdder,
    settings: CathodeSettings,
    store: CathodeSettingsStore,
) {
    val albumCount = remember(state.tracks) { state.tracks.map(AudioTrack::album).distinct().size }
    val pinned = remember(state.tracks, settings.pinnedTrackKeys) { state.tracks.filter { it.stableKey in settings.pinnedTrackKeys } }
    fun togglePin(track: AudioTrack) = store.update {
        it.copy(pinnedTrackKeys = if (track.stableKey in it.pinnedTrackKeys) it.pinnedTrackKeys - track.stableKey else it.pinnedTrackKeys + track.stableKey)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Your music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${state.tracks.size} songs · $albumCount albums · available offline", color = CathodeMuted)
                }
                IconButton(onClick = onRescan, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Rescan library", tint = CathodeCyan) }
            }
        }
        if (state.tracks.isNotEmpty()) item {
            Button(onClick = { onPlay(state.tracks.first()) }) {
                Icon(Icons.Default.PlayArrow, null); Text("Play all", modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.tracks.isEmpty()) item { EmptyLibrary(state.permissionGranted) }
        val recent = state.recentTrackKeys.mapNotNull { key -> state.tracks.firstOrNull { it.stableKey == key } }.take(12)
        val mostPlayed = state.tracks.filter { (state.playCounts[it.stableKey] ?: 0) > 0 }
            .sortedByDescending { state.playCounts[it.stableKey] ?: 0 }.take(12)
        settings.homeSections.filterNot(settings.hiddenHomeSections::contains).forEach { section ->
            when (section) {
                "Pinned" -> if (pinned.isNotEmpty()) {
                    item { SectionLabel("Pinned") }
                    items(pinned, key = { "pinned-${it.id}" }) { TrackRow(it,onPlay,onEdit,onReset,true,{togglePin(it)},it.stableKey in state.favoriteKeys,{onToggleFavorite(it)},state.playlists,{ id -> onAddToPlaylist(id,it) }) }
                }
                "Recently played" -> if (recent.isNotEmpty()) {
                    item { SectionLabel("Recently played") }
                    items(recent, key = { "played-${it.stableKey}" }) {
                        TrackRow(it,onPlay,onEdit,onReset,false,null,it.stableKey in state.favoriteKeys,{onToggleFavorite(it)},state.playlists,{ id -> onAddToPlaylist(id,it) })
                    }
                }
                "Most played" -> if (mostPlayed.isNotEmpty()) {
                    item { SectionLabel("Most played") }
                    items(mostPlayed, key = { "most-${it.stableKey}" }) {
                        TrackRow(it,onPlay,onEdit,onReset,false,null,it.stableKey in state.favoriteKeys,{onToggleFavorite(it)},state.playlists,{ id -> onAddToPlaylist(id,it) })
                    }
                }
                "Recently added" -> if (state.tracks.isNotEmpty()) {
                    item { SectionLabel("Recently added") }
                    items(state.tracks.take(12), key = { "recent-${it.id}" }) {
                        TrackRow(it,onPlay,onEdit,onReset,it.stableKey in settings.pinnedTrackKeys,{togglePin(it)},it.stableKey in state.favoriteKeys,{onToggleFavorite(it)},state.playlists,{ id -> onAddToPlaylist(id,it) })
                    }
                }
            }
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
    onReset: MetadataResetter,
    onToggleFavorite: FavoriteToggler,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddToPlaylist: PlaylistAdder,
    onRemoveFromPlaylist: (Long, AudioTrack) -> Unit,
    settings: CathodeSettings,
    store: CathodeSettingsStore,
) {
    var selectedGroup by remember(settings.libraryCategory) { mutableStateOf<String?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    val sorted = remember(state.tracks, settings.librarySort) { sortTracks(state.tracks, settings.librarySort) }
    val favorites = sorted.filter { it.stableKey in state.favoriteKeys }
    val groups = remember(sorted, settings.libraryCategory, state.playlists, state.playlistTrackKeys) {
        when (settings.libraryCategory) {
            LibraryCategory.SONGS, LibraryCategory.FAVORITES -> emptyList()
            LibraryCategory.ALBUMS -> sorted.groupBy { "${it.artist}\u0000${it.album}" }
                .map { (key, tracks) -> LibraryGroup(key, tracks.first().album, tracks.first().artist, tracks) }
            LibraryCategory.ARTISTS -> sorted.groupBy(AudioTrack::artist)
                .map { (key, tracks) -> LibraryGroup(key, key, "${tracks.size} songs", tracks) }
            LibraryCategory.FOLDERS -> sorted.groupBy { it.relativePath ?: "Unknown folder" }
                .map { (key, tracks) -> LibraryGroup(key, key.trimEnd('/').substringAfterLast('/'), "${tracks.size} songs", tracks) }
            LibraryCategory.PLAYLISTS -> state.playlists.map { playlist ->
                val keys = state.playlistTrackKeys[playlist.id].orEmpty()
                val tracks = keys.mapNotNull { key -> sorted.firstOrNull { it.stableKey == key } }
                LibraryGroup("playlist:${playlist.id}", playlist.name, "${playlist.trackCount} songs", tracks)
            }
        }.sortedBy { it.title.lowercase() }
    }
    val detail = groups.firstOrNull { it.key == selectedGroup }
    val detailPlaylistId = detail?.key?.removePrefix("playlist:")?.toLongOrNull()
    BackHandler(enabled = detail != null) { selectedGroup = null }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (detail != null) IconButton(onClick = { selectedGroup = null }) {
                Icon(Icons.Default.ArrowBack, "Back to library", tint = CathodeCyan)
            }
            Column(Modifier.weight(1f)) {
                Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(detail?.title ?: "Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(detail?.subtitle ?: "${state.tracks.size} local tracks", color = CathodeMuted)
            }
            if (settings.libraryCategory == LibraryCategory.PLAYLISTS && detail == null) {
                IconButton(onClick = { creatingPlaylist = true }) { Icon(Icons.Default.Add, "Create playlist", tint = CathodeCyan) }
            }
            if (detailPlaylistId != null) {
                IconButton(onClick = { onDeletePlaylist(detailPlaylistId); selectedGroup = null }) {
                    Icon(Icons.Default.Delete, "Delete playlist", tint = CathodeError)
                }
            }
            IconButton(onClick = onRescan, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Rescan", tint = CathodeCyan) }
        }
        when {
            !state.permissionGranted -> PermissionPanel(requestPermission)
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CathodeCyan) }
            state.error != null -> MessagePanel("Library scan failed", state.error, CathodeError)
            state.tracks.isEmpty() -> EmptyLibrary(true)
            detail != null -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(detail.tracks, key = AudioTrack::stableKey) { track ->
                    TrackRow(
                        track,onPlay,onEdit,onReset,false,null,
                        track.stableKey in state.favoriteKeys,{onToggleFavorite(track)},
                        state.playlists,{ id -> onAddToPlaylist(id,track) },
                        if (detailPlaylistId != null) ({ onRemoveFromPlaylist(detailPlaylistId, track) }) else null,
                    )
                }
                if (detail.tracks.isEmpty()) item { MessagePanel("Empty playlist", "Add songs using the playlist button beside any track.", CathodeMuted) }
                item { Spacer(Modifier.height(12.dp)) }
            }
            else -> {
                Column {
                    LibraryCategory.entries.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { category ->
                                FilterChip(
                                    selected = settings.libraryCategory == category,
                                    onClick = { store.update { it.copy(libraryCategory = category) } },
                                    label = { Text(category.label) },
                                )
                            }
                        }
                    }
                }
                if (settings.libraryCategory != LibraryCategory.PLAYLISTS) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LibrarySort.entries.forEach { sort ->
                            FilterChip(
                                selected = settings.librarySort == sort,
                                onClick = { store.update { it.copy(librarySort = sort) } },
                                label = { Text(sort.label) },
                            )
                        }
                    }
                }
                if (settings.libraryCategory !in listOf(LibraryCategory.SONGS, LibraryCategory.FAVORITES)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilterChip(
                            selected = settings.libraryGrid,
                            onClick = { store.update { it.copy(libraryGrid = !it.libraryGrid) } },
                            label = { Text(if (settings.libraryGrid) "Grid" else "List") },
                        )
                    }
                }
                val directTracks = if (settings.libraryCategory == LibraryCategory.FAVORITES) favorites else sorted
                if (settings.libraryCategory in listOf(LibraryCategory.SONGS, LibraryCategory.FAVORITES)) {
                    if (directTracks.isEmpty()) MessagePanel("Nothing here yet", "Favorite songs with the star button.", CathodeMuted)
                    else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(directTracks, key = AudioTrack::stableKey) { track ->
                            TrackRow(track,onPlay,onEdit,onReset,false,null,track.stableKey in state.favoriteKeys,
                                {onToggleFavorite(track)},state.playlists,{ id -> onAddToPlaylist(id,track) })
                        }
                    }
                } else if (groups.isEmpty()) {
                    MessagePanel("No playlists", "Create a playlist with the plus button.", CathodeMuted)
                } else if (settings.libraryGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp), modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { gridItems(groups, key = LibraryGroup::key) { group -> LibraryGroupCard(group) { selectedGroup = group.key } } }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(groups, key = LibraryGroup::key) { group -> LibraryGroupRow(group) { selectedGroup = group.key } }
                    }
                }
            }
        }
    }

    if (creatingPlaylist) AlertDialog(
        onDismissRequest = { creatingPlaylist = false },
        title = { Text("New playlist") },
        text = { OutlinedTextField(playlistName, { playlistName = it }, label = { Text("Playlist name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            if (playlistName.isNotBlank()) onCreatePlaylist(playlistName)
            playlistName = ""; creatingPlaylist = false
        }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { creatingPlaylist = false }) { Text("Cancel") } },
    )
}

private data class LibraryGroup(val key: String, val title: String, val subtitle: String, val tracks: List<AudioTrack>)

@Composable
private fun LibraryGroupRow(group: LibraryGroup, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(group.tracks.firstOrNull()?.artworkUri, null, Modifier.size(58.dp).background(CathodePanel), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(group.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(group.subtitle, color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        Text("${group.tracks.size}", color = CathodeDim)
    }
}

@Composable
private fun LibraryGroupCard(group: LibraryGroup, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column {
            AsyncImage(group.tracks.firstOrNull()?.artworkUri, null, Modifier.fillMaxWidth().aspectRatio(1f).background(CathodePanel), contentScale = ContentScale.Crop)
            Text(group.title, Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(group.subtitle, Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

private fun sortTracks(tracks: List<AudioTrack>, sort: LibrarySort): List<AudioTrack> = when (sort) {
    LibrarySort.RECENT -> tracks.sortedByDescending(AudioTrack::dateAddedSeconds)
    LibrarySort.TITLE -> tracks.sortedBy { it.title.lowercase() }
    LibrarySort.ARTIST -> tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, AudioTrack::trackNumber))
    LibrarySort.ALBUM -> tracks.sortedWith(compareBy({ it.album.lowercase() }, AudioTrack::trackNumber))
    LibrarySort.DURATION -> tracks.sortedByDescending(AudioTrack::durationMs)
}

@Composable
fun SearchScreen(
    tracks: List<AudioTrack>,
    onPlay: (AudioTrack) -> Unit,
    onEdit: MetadataEditor,
    onReset: MetadataResetter,
    favoriteKeys: Set<String>,
    playlists: List<PlaylistSummary>,
    onToggleFavorite: FavoriteToggler,
    onAddToPlaylist: PlaylistAdder,
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
                items(results, key = AudioTrack::stableKey) { track -> TrackRow(track,onPlay,onEdit,onReset,false,null,track.stableKey in favoriteKeys,{onToggleFavorite(track)},playlists,{ id -> onAddToPlaylist(id,track) }) }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: AudioTrack, onPlay: (AudioTrack) -> Unit, onEdit: MetadataEditor, onReset: MetadataResetter,
    pinned: Boolean = false, onPin: (() -> Unit)? = null,
    favorite: Boolean = false, onToggleFavorite: (() -> Unit)? = null,
    playlists: List<PlaylistSummary> = emptyList(), onAddToPlaylist: ((Long) -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    var editing by remember(track.stableKey) { mutableStateOf(false) }
    var choosingPlaylist by remember(track.stableKey) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(track.artworkUri, "${track.album} cover", Modifier.size(52.dp).background(CathodePanel), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist} · ${track.album}", color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.tags.isNotEmpty()) Text(track.tags, color = CathodeDim, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        if (onPin != null) IconButton(onClick = onPin) { Icon(if (pinned) Icons.Default.Star else Icons.Outlined.StarOutline, if (pinned) "Unpin" else "Pin", tint = if (pinned) CathodeCyan else CathodeMuted) }
        if (onToggleFavorite != null) IconButton(onClick = onToggleFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (favorite) "Remove favorite" else "Favorite", tint = if (favorite) CathodeCyan else CathodeMuted) }
        if (onAddToPlaylist != null) IconButton(onClick = { choosingPlaylist = true }) { Icon(Icons.Default.PlaylistAdd, "Add to playlist", tint = CathodeMuted) }
        if (onRemoveFromPlaylist != null) IconButton(onClick = onRemoveFromPlaylist) { Icon(Icons.Default.Delete, "Remove from playlist", tint = CathodeMuted) }
        IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit metadata", tint = CathodeMuted) }
    }
    if (editing) MetadataDialog(track,{ editing = false },{ onReset(track); editing = false }) { title,artist,album,tags,artworkUri ->
        onEdit(track,title,artist,album,tags,artworkUri); editing = false
    }
    if (choosingPlaylist) AlertDialog(
        onDismissRequest = { choosingPlaylist = false }, title = { Text("Add to playlist") },
        text = { Column {
            if (playlists.isEmpty()) Text("Create a playlist from the Playlists library tab.", color = CathodeMuted)
            playlists.forEach { playlist -> TextButton(onClick = { onAddToPlaylist?.invoke(playlist.id); choosingPlaylist = false }) { Text("${playlist.name} · ${playlist.trackCount}") } }
        } },
        confirmButton = {}, dismissButton = { TextButton(onClick = { choosingPlaylist = false }) { Text("Cancel") } },
    )
}

@Composable
private fun MetadataDialog(
    track: AudioTrack,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onSave: (String, String, String, String, String?) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var tags by remember(track.id) { mutableStateOf(track.tags) }
    var artworkUri by remember(track.id) { mutableStateOf(track.customArtworkUri) }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            artworkUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit track details") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(
                    model = artworkUri ?: track.artworkUri,
                    contentDescription = "Selected cover",
                    modifier = Modifier.size(112.dp).align(Alignment.CenterHorizontally),
                    contentScale = ContentScale.Crop,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { artworkPicker.launch(arrayOf("image/*")) }) { Text("Choose cover") }
                    if (artworkUri != null) {
                        TextButton(onClick = { artworkUri = null }) { Text("Use original") }
                    }
                }
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
                    buildString {
                        append(track.mimeType?.substringAfter('/')?.uppercase() ?: "AUDIO")
                        if (track.year > 0) append(" · ${track.year}")
                        if (track.trackNumber > 0) append(" · TRACK ${track.trackNumber}")
                        append(" · ${"%.1f".format(track.fileSize / 1_048_576.0)} MB")
                        append("\n${track.displayName}")
                        track.relativePath?.let { append("\n$it") }
                    },
                    color = CathodeDim,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "Changes are stored in Cathode and do not rewrite the original audio file.",
                    color = CathodeMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, artist, album, tags, artworkUri) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
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
