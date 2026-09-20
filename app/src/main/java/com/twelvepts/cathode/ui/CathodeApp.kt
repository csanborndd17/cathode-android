package com.twelvepts.cathode.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.twelvepts.cathode.CathodeViewModel
import com.twelvepts.cathode.R
import com.twelvepts.cathode.playback.PlayerConnection
import kotlinx.coroutines.delay

enum class CathodeTab(val label: String, val icon: ImageVector) {
    Home("HOME", Icons.Default.Home),
    Search("SEARCH", Icons.Default.Search),
    Library("LIBRARY", Icons.Default.LibraryMusic),
    Acquire("ACQUIRE", Icons.Default.CloudDownload),
}

@Composable
fun CathodeApp(
    viewModel: CathodeViewModel,
    player: PlayerConnection,
    requestPermission: () -> Unit,
) {
    var tab by remember { mutableStateOf(CathodeTab.Home) }
    var showPlayer by remember { mutableStateOf(false) }
    var showStartup by remember { mutableStateOf(true) }
    var logoRevealed by remember { mutableStateOf(false) }
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()

    BackHandler(enabled = !showPlayer && tab != CathodeTab.Home) { tab = CathodeTab.Home }

    LaunchedEffect(Unit) {
        logoRevealed = true
        delay(1_250)
        showStartup = false
    }

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
                    MiniPlayer(
                        title = playback.title,
                        artist = playback.artist,
                        artworkUri = playback.artworkUri,
                        isPlaying = playback.isPlaying,
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        onOpen = { showPlayer = true },
                        onToggle = player::togglePlayPause,
                    )
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
                CathodeTab.Home -> HomeScreen(
                    state = library,
                    onRescan = viewModel::rescan,
                    onPlay = { player.play(library.tracks, it) },
                    onEdit = viewModel::updateMetadata,
                )
                CathodeTab.Search -> SearchScreen(
                    tracks = library.tracks,
                    onPlay = { player.play(library.tracks, it) },
                    onEdit = viewModel::updateMetadata,
                )
                CathodeTab.Library -> LibraryScreen(
                    state = library,
                    requestPermission = requestPermission,
                    onRescan = viewModel::rescan,
                    onPlay = { player.play(library.tracks, it) },
                    onEdit = viewModel::updateMetadata,
                )
                CathodeTab.Acquire -> AcquireScreen(onDownloadStarted = viewModel::rescan)
            }
        }
    }

    if (showPlayer) {
        NowPlayingScreen(playback, player, onDismiss = { showPlayer = false })
    }

    AnimatedVisibility(visible = showStartup, exit = fadeOut(tween(450))) {
        StartupReveal(logoRevealed)
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    artworkUri: android.net.Uri?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val view = LocalView.current
    Box(
        Modifier.fillMaxWidth().height(64.dp).background(CathodePanel).clickable(onClick = onOpen),
    ) {
        AsyncImage(
            model = artworkUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(8.dp).alpha(.38f),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(CathodePanel.copy(alpha = .45f), CathodePanel.copy(alpha = .92f)),
                ),
            ),
        )
        LinearProgressIndicator(
            progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
            color = CathodeCyan,
            trackColor = CathodeDim.copy(alpha = .35f),
        )
        Row(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onToggle()
                },
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = CathodeCyan,
                )
            }
        }
    }
}

@Composable
private fun StartupReveal(revealed: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else .72f,
        animationSpec = tween(700),
        label = "startup-logo-scale",
    )
    val opacity by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(500),
        label = "startup-logo-opacity",
    )
    Box(Modifier.fillMaxSize().background(CathodeBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_cathode),
                contentDescription = "Cathode",
                modifier = Modifier.size(156.dp).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = opacity
                },
            )
            Text(
                "CATHODE",
                color = CathodeCyan,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp).alpha(opacity),
            )
            Text(
                "12PTS",
                color = CathodeMuted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp).alpha(opacity),
            )
        }
    }
}
