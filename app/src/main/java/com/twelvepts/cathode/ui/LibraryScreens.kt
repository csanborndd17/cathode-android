package com.twelvepts.cathode.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twelvepts.cathode.LibraryState
import com.twelvepts.cathode.data.PlaylistSummary
import com.twelvepts.cathode.model.AudioTrack
import java.util.Calendar

typealias MetadataEditor = (AudioTrack, String, String, String, String, String?) -> Unit
typealias MetadataResetter = (AudioTrack) -> Unit
typealias FavoriteToggler = (AudioTrack) -> Unit
typealias PlaylistAdder = (Long, AudioTrack) -> Unit

val LocalPlayNext = staticCompositionLocalOf<(AudioTrack) -> Unit> { {} }
val LocalAddToQueue = staticCompositionLocalOf<(AudioTrack) -> Unit> { {} }

@Composable
fun HomeScreen(
    state: LibraryState,
    onRescan: () -> Unit,
    onPlay: (AudioTrack) -> Unit,
    onEdit: MetadataEditor,
    onReset: MetadataResetter,
    onToggleFavorite: FavoriteToggler,
    onAddToPlaylist: PlaylistAdder,
    onProfile: () -> Unit,
    onOpenLibrary: (LibraryCategory) -> Unit,
    currentTrackKey: String?,
    currentIsPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    settings: CathodeSettings,
    store: CathodeSettingsStore,
) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val signal = state.transmissionYears[currentYear]
    val recent = state.recentTrackKeys.mapNotNull { key -> state.tracks.firstOrNull { it.stableKey == key } }
    val current = state.tracks.firstOrNull { it.stableKey == currentTrackKey }
    val hero = current ?: recent.firstOrNull() ?: state.tracks.firstOrNull()
    val mostPlayed = state.tracks.filter { (state.playCounts[it.stableKey] ?: 0) > 0 }.sortedByDescending { state.playCounts[it.stableKey] ?: 0 }.take(10)
    val lossless = state.tracks.filter { track ->
        val type = track.mimeType.orEmpty().lowercase()
        type.contains("flac") || type.contains("alac") || type.contains("wav")
    }.take(10)
    val rediscover = state.tracks.filter { it.stableKey in state.favoriteKeys && it !in recent.take(12) }.take(10)
    val albums = recent.groupBy { it.album }.values.mapNotNull { it.firstOrNull() }.take(10)
    fun visible(name: String) = name !in settings.hiddenHomeSections

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(settings.profileImageUri, "Open profile", Modifier.size(46.dp).clip(CircleShape).background(CathodeDim).clickable(onClick = onProfile), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Home", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${state.tracks.size} local tracks", color = CathodeMuted)
                }
                IconButton(onClick = onRescan, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Rescan library", tint = CathodeCyan) }
            }
        }
        if (hero != null && visible("Continue listening")) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val heroIsCurrent = current?.stableKey == hero.stableKey
                Card(onClick = { if (heroIsCurrent) onTogglePlayback() else onPlay(hero) }, modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(hero.artworkUri, "${hero.album} cover", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, CathodeBlack.copy(alpha = .92f)))))
                        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 82.dp, bottom = 18.dp)) {
                            Text(if (current != null) "NOW TRANSMITTING" else "CONTINUE LISTENING", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            MarqueeText(hero.title, MaterialTheme.typography.headlineMedium, CathodeText, FontWeight.Bold)
                            MarqueeText("${hero.artist} · ${hero.album}", MaterialTheme.typography.bodyMedium, CathodeMuted)
                        }
                        IconButton(
                            onClick = { if (heroIsCurrent) onTogglePlayback() else onPlay(hero) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp).size(52.dp).clip(CircleShape).background(CathodeCyan),
                        ) {
                            val playing = heroIsCurrent && currentIsPlaying
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", tint = CathodeBlack, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
        if (state.tracks.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyLibrary(state.permissionGranted) }
        } else if (visible("Quick transmission")) {
            item { HomeMetricCard("SHUFFLE", "${state.tracks.size} tracks", Icons.Default.PlayArrow, 0) { onPlay(state.tracks.random()) } }
            item { HomeMetricCard("FAVORITES", "${state.favoriteKeys.size} saved", Icons.Default.Favorite, 1) { onOpenLibrary(LibraryCategory.FAVORITES) } }
            item { HomeMetricCard("LOSSLESS", "${lossless.size} indexed", Icons.Default.Star, 2) { onOpenLibrary(LibraryCategory.SONGS) } }
            item { HomeMetricCard("PLAYLISTS", "${state.playlists.size} collections", Icons.Default.PlaylistAdd, 3) { onOpenLibrary(LibraryCategory.PLAYLISTS) } }
        }
        if (visible("Transmission snapshot")) {
            item(span = { GridItemSpan(maxLineSpan) }) { HomeSectionTitle("Transmission snapshot") }
            item { HomeNumberCard("${signal?.totalListenedMs?.div(60_000) ?: 0}", "MINUTES THIS YEAR") }
            item { HomeNumberCard("${signal?.totalPlays ?: 0}", "PLAYS THIS YEAR") }
        }
        if (mostPlayed.isNotEmpty() && visible("On repeat")) item(span = { GridItemSpan(maxLineSpan) }) { HomeTrackShelf("On repeat", mostPlayed, onPlay) }
        if (recent.isNotEmpty() && visible("Recently played")) item(span = { GridItemSpan(maxLineSpan) }) { HomeTrackShelf("Recently played", recent.take(10), onPlay) }
        if (albums.isNotEmpty() && visible("Albums in progress")) item(span = { GridItemSpan(maxLineSpan) }) { HomeTrackShelf("Albums in progress", albums, onPlay) }
        if (rediscover.isNotEmpty() && visible("Rediscover")) item(span = { GridItemSpan(maxLineSpan) }) { HomeTrackShelf("Rediscover", rediscover, onPlay) }
        if (lossless.isNotEmpty() && visible("Lossless shelf")) item(span = { GridItemSpan(maxLineSpan) }) { HomeTrackShelf("Lossless shelf", lossless, onPlay) }
        if (state.playlists.isNotEmpty() && visible("Pinned playlists")) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    HomeSectionTitle("Your playlists")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.playlists) { playlist ->
                            Card(onClick = { onOpenLibrary(LibraryCategory.PLAYLISTS) }, modifier = Modifier.size(width = 170.dp, height = 92.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Text(playlist.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${playlist.trackCount} tracks", color = CathodeMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun HomeMetricCard(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, pattern: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(112.dp)) {
        Box(Modifier.fillMaxSize().background(CathodePanel)) {
            Canvas(Modifier.fillMaxSize()) {
                val accent = when (pattern) { 1 -> Color(0xFF66FFF0); 2 -> Color(0xFF4CB7FF); 3 -> Color(0xFF99FFE8); else -> CathodeCyan }
                when (pattern) {
                    0 -> for (x in -size.height.toInt() until size.width.toInt() step 22) drawLine(accent.copy(alpha = .12f), androidx.compose.ui.geometry.Offset(x.toFloat(), size.height), androidx.compose.ui.geometry.Offset(x + size.height, 0f), 2f)
                    1 -> for (x in 12..size.width.toInt() step 26) for (y in 10..size.height.toInt() step 26) drawCircle(accent.copy(alpha = if ((x + y) % 52 == 0) .18f else .08f), if ((x + y) % 52 == 0) 5f else 2.5f, androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat()))
                    2 -> repeat(5) { ring -> drawCircle(accent.copy(alpha = .12f - ring * .015f), 24f + ring * 22f, androidx.compose.ui.geometry.Offset(size.width, 0f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f)) }
                    else -> { for (x in 0..size.width.toInt() step 32) drawLine(accent.copy(alpha = .07f), androidx.compose.ui.geometry.Offset(x.toFloat(), 0f), androidx.compose.ui.geometry.Offset(x.toFloat(), size.height), 1f); for (y in 0..size.height.toInt() step 32) drawLine(accent.copy(alpha = .07f), androidx.compose.ui.geometry.Offset(0f, y.toFloat()), androidx.compose.ui.geometry.Offset(size.width, y.toFloat()), 1f) }
                }
            }
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, null, tint = CathodeCyan)
                Column { Text(title, fontWeight = FontWeight.Bold); Text(detail, color = CathodeMuted, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarqueeText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, weight: FontWeight? = null) {
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = weight,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, .07f to Color.Black, .90f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }.basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 1200),
    )
}

@Composable
private fun HomeNumberCard(value: String, label: String) {
    Card(Modifier.fillMaxWidth().height(96.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text(value, color = CathodeCyan, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, color = CathodeMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun HomeSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
}

@Composable
private fun HomeTrackShelf(title: String, tracks: List<AudioTrack>, onPlay: (AudioTrack) -> Unit) {
    Column {
        HomeSectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tracks, key = AudioTrack::stableKey) { track ->
                Card(onClick = { onPlay(track) }, modifier = Modifier.size(width = 150.dp, height = 205.dp)) {
                    Column {
                        AsyncImage(track.artworkUri, "${track.album} cover", Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
                        Text(track.title, Modifier.padding(start = 9.dp, end = 9.dp, top = 7.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, Modifier.padding(horizontal = 9.dp), color = CathodeMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
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
    onCreatePlaylistWithTracks: (String, List<AudioTrack>) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddToPlaylist: PlaylistAdder,
    onAddTracksToPlaylist: (Long, List<AudioTrack>) -> Unit,
    onRemoveFromPlaylist: (Long, AudioTrack) -> Unit,
    onProfile: () -> Unit,
    settings: CathodeSettings,
    store: CathodeSettingsStore,
) {
    var selectedGroup by remember(settings.libraryCategory) { mutableStateOf<String?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var libraryQuery by remember { mutableStateOf("") }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var choosingBulkPlaylist by remember { mutableStateOf(false) }
    var creatingBulkPlaylist by remember { mutableStateOf(false) }
    var bulkPlaylistName by remember { mutableStateOf("") }
    var confirmingDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedTracks = state.tracks.filter { it.stableKey in selectedKeys }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedKeys = emptySet()
            onRescan()
        }
    }
    val filteredTracks = remember(state.tracks, libraryQuery) {
        if (libraryQuery.isBlank()) state.tracks else state.tracks.filter {
            it.title.contains(libraryQuery, true) || it.artist.contains(libraryQuery, true) ||
                it.album.contains(libraryQuery, true) || it.tags.contains(libraryQuery, true)
        }
    }
    val sorted = remember(filteredTracks, settings.librarySort) { sortTracks(filteredTracks, settings.librarySort) }
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
    BackHandler(enabled = selectedKeys.isNotEmpty()) { selectedKeys = emptySet() }
    BackHandler(enabled = selectedKeys.isEmpty() && detail != null) { selectedGroup = null }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (detail != null) IconButton(onClick = { selectedGroup = null }) {
                Icon(Icons.Default.ArrowBack, "Back to library", tint = CathodeCyan)
            } else {
                AsyncImage(settings.profileImageUri, "Open profile", Modifier.size(44.dp).clip(CircleShape).background(CathodeDim).clickable(onClick = onProfile), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
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
            IconButton(onClick = { searching = !searching }) { Icon(Icons.Default.Search, "Search library", tint = CathodeCyan) }
            IconButton(onClick = onRescan, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Rescan", tint = CathodeCyan) }
        }
        if (selectedKeys.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).background(CathodePanel).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${selectedKeys.size} selected", Modifier.weight(1f), color = CathodeCyan, fontWeight = FontWeight.Bold)
                IconButton(onClick = { choosingBulkPlaylist = true }) {
                    Icon(Icons.Default.PlaylistAdd, "Add selected tracks to playlist", tint = CathodeCyan)
                }
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(Icons.Default.Delete, "Delete selected files from device", tint = CathodeError)
                }
            }
        }
        if (searching && detail == null) OutlinedTextField(
            value = libraryQuery, onValueChange = { libraryQuery = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("Search songs, artists, albums, and tags") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
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
                        selected = track.stableKey in selectedKeys,
                        onLongClick = { selectedKeys = selectedKeys + track.stableKey },
                        onSelectionClick = if (selectedKeys.isNotEmpty()) ({ selectedKeys = selectedKeys.toggle(track.stableKey) }) else null,
                    )
                }
                if (detail.tracks.isEmpty()) item { MessagePanel("Empty playlist", "Add songs using the playlist button beside any track.", CathodeMuted) }
                item { Spacer(Modifier.height(12.dp)) }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Button(onClick = { categoryMenu = true }) { Text("${settings.libraryCategory.label} ▼") }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            LibraryCategory.entries.forEach { category ->
                                DropdownMenuItem(text = { Text(category.label) }, onClick = {
                                    store.update { it.copy(libraryCategory = category) }
                                    categoryMenu = false
                                })
                            }
                        }
                    }
                    if (settings.libraryCategory != LibraryCategory.PLAYLISTS) Box {
                        TextButton(onClick = { sortMenu = true }) { Text("Sort: ${settings.librarySort.label}") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            LibrarySort.entries.forEach { sort ->
                                DropdownMenuItem(text = { Text(sort.label) }, onClick = {
                                    store.update { it.copy(librarySort = sort) }
                                    sortMenu = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (settings.libraryCategory !in listOf(LibraryCategory.SONGS, LibraryCategory.FAVORITES)) {
                        FilterChip(selected = settings.libraryGrid, onClick = { store.update { it.copy(libraryGrid = !it.libraryGrid) } },
                            label = { Text(if (settings.libraryGrid) "Grid" else "List") })
                    }
                }
                val directTracks = if (settings.libraryCategory == LibraryCategory.FAVORITES) favorites else sorted
                if (settings.libraryCategory in listOf(LibraryCategory.SONGS, LibraryCategory.FAVORITES)) {
                    if (directTracks.isEmpty()) MessagePanel("Nothing here yet", "Favorite songs with the star button.", CathodeMuted)
                    else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(directTracks, key = AudioTrack::stableKey) { track ->
                            TrackRow(track,onPlay,onEdit,onReset,false,null,track.stableKey in state.favoriteKeys,
                                {onToggleFavorite(track)},state.playlists,{ id -> onAddToPlaylist(id,track) },
                                selected = track.stableKey in selectedKeys,
                                onLongClick = { selectedKeys = selectedKeys + track.stableKey },
                                onSelectionClick = if (selectedKeys.isNotEmpty()) ({ selectedKeys = selectedKeys.toggle(track.stableKey) }) else null)
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

    if (choosingBulkPlaylist) AlertDialog(
        onDismissRequest = { choosingBulkPlaylist = false },
        title = { Text("Add ${selectedKeys.size} tracks to playlist") },
        text = { Column {
            TextButton(onClick = { choosingBulkPlaylist = false; creatingBulkPlaylist = true }) {
                Icon(Icons.Default.Add, null)
                Text(" Create new playlist")
            }
            state.playlists.forEach { playlist ->
                TextButton(onClick = {
                    onAddTracksToPlaylist(playlist.id, selectedTracks)
                    selectedKeys = emptySet()
                    choosingBulkPlaylist = false
                }) { Text("${playlist.name} · ${playlist.trackCount}") }
            }
        } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { choosingBulkPlaylist = false }) { Text("Cancel") } },
    )

    if (creatingBulkPlaylist) AlertDialog(
        onDismissRequest = { creatingBulkPlaylist = false },
        title = { Text("New playlist") },
        text = { OutlinedTextField(bulkPlaylistName, { bulkPlaylistName = it }, label = { Text("Playlist name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            if (bulkPlaylistName.isNotBlank()) {
                onCreatePlaylistWithTracks(bulkPlaylistName, selectedTracks)
                selectedKeys = emptySet()
            }
            bulkPlaylistName = ""
            creatingBulkPlaylist = false
        }) { Text("Create and add") } },
        dismissButton = { TextButton(onClick = { creatingBulkPlaylist = false }) { Text("Cancel") } },
    )

    if (confirmingDelete) AlertDialog(
        onDismissRequest = { confirmingDelete = false },
        title = { Text("Delete ${selectedKeys.size} files from device?") },
        text = { Text("This permanently removes the selected audio files from device storage, not just from Cathode. This cannot be undone.") },
        confirmButton = { TextButton(onClick = {
            confirmingDelete = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val request = MediaStore.createDeleteRequest(context.contentResolver, selectedTracks.map(AudioTrack::uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                selectedTracks.forEach { runCatching { context.contentResolver.delete(it.uri, null, null) } }
                selectedKeys = emptySet()
                onRescan()
            }
        }) { Text("Delete permanently", color = CathodeError) } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
    )
}

private fun Set<String>.toggle(key: String): Set<String> = if (key in this) this - key else this + key

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: AudioTrack, onPlay: (AudioTrack) -> Unit, onEdit: MetadataEditor, onReset: MetadataResetter,
    pinned: Boolean = false, onPin: (() -> Unit)? = null,
    favorite: Boolean = false, onToggleFavorite: (() -> Unit)? = null,
    playlists: List<PlaylistSummary> = emptyList(), onAddToPlaylist: ((Long) -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onSelectionClick: (() -> Unit)? = null,
) {
    var editing by remember(track.stableKey) { mutableStateOf(false) }
    var choosingPlaylist by remember(track.stableKey) { mutableStateOf(false) }
    var moreMenu by remember(track.stableKey) { mutableStateOf(false) }
    val playNext = LocalPlayNext.current
    val addToQueue = LocalAddToQueue.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) CathodeCyan.copy(alpha = .16f) else Color.Transparent)
            .combinedClickable(
                onClick = { if (onSelectionClick != null) onSelectionClick() else onPlay(track) },
                onLongClick = onLongClick,
            )
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Box {
            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "Track actions", tint = CathodeMuted) }
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.Default.SkipNext, null) },
                    onClick = { playNext(track); moreMenu = false },
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, null) },
                    onClick = { addToQueue(track); moreMenu = false },
                )
                DropdownMenuItem(
                    text = { Text("Edit metadata") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { editing = true; moreMenu = false },
                )
            }
        }
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
