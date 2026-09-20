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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twelvepts.cathode.CathodeViewModel
import com.twelvepts.cathode.playback.PlayerConnection
import kotlinx.coroutines.delay

enum class CathodeTab(val label: String, val icon: ImageVector) {
    Home("HOME", Icons.Default.Home),
    Search("SEARCH", Icons.Default.Search),
    Library("LIBRARY", Icons.Default.LibraryMusic),
    Acquire("ACQUIRE", Icons.Default.Terminal),
}

@Composable
fun CathodeApp(
    viewModel: CathodeViewModel,
    player: PlayerConnection,
    requestPermission: () -> Unit,
) {
    var tab by remember { mutableStateOf(CathodeTab.Home) }
    var showPlayer by remember { mutableStateOf(false) }
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()

    LaunchedEffect(playback.connected, playback.isPlaying) {
        while (playback.connected) {
            player.refreshPosition()
            delay(500)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CathodeBlack,
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                if (playback.title.isNotEmpty()) {
                    MiniPlayer(playback.title, playback.artist, playback.isPlaying, {
                        showPlayer = true
                    }, player::togglePlayPause)
                }
                NavigationBar(containerColor = CathodePanel) {
                    CathodeTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CathodeBlack,
                                selectedTextColor = CathodeCyan,
                                indicatorColor = CathodeCyan,
                                unselectedIconColor = CathodeMuted,
                                unselectedTextColor = CathodeMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                CathodeTab.Home -> HomeScreen(library, onRescan = viewModel::rescan, onPlay = { player.play(library.tracks, it) })
                CathodeTab.Search -> SearchScreen(library.tracks, onPlay = { player.play(library.tracks, it) })
                CathodeTab.Library -> LibraryScreen(library, requestPermission, viewModel::rescan) { player.play(library.tracks, it) }
                CathodeTab.Acquire -> AcquireScreen(onDownloadStarted = viewModel::rescan)
            }
        }
    }

    if (showPlayer) {
        NowPlayingScreen(playback, player, onDismiss = { showPlayer = false })
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(CathodePanel).clickable(onClick = onOpen),
    ) {
        HorizontalDivider(color = CathodeCyan)
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(">", color = CathodeCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = CathodeCyan,
                )
            }
        }
    }
}
