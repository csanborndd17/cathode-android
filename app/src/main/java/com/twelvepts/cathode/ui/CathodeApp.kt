package com.twelvepts.cathode.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch

enum class CathodeTab(val label: String, val icon: ImageVector) {
    Home("HOME", Icons.Default.Home),
    Library("LIBRARY", Icons.Default.LibraryMusic),
    Discover("DISCOVER", Icons.Default.CloudDownload),
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CathodeApp(
    viewModel: CathodeViewModel,
    player: PlayerConnection,
    requestPermission: () -> Unit,
    settings: CathodeSettings,
    settingsStore: CathodeSettingsStore,
) {
    fun resolveTab(name: String): CathodeTab = when (name) {
        "Listen" -> CathodeTab.Home
        "Acquire" -> CathodeTab.Discover
        else -> runCatching { CathodeTab.valueOf(name) }.getOrDefault(CathodeTab.Home)
    }
    var tab by remember {
        val target = if (settings.startupDestination == "Remember") settings.lastTab else settings.startupDestination
        mutableStateOf(resolveTab(target))
    }
    var previousTab by remember { mutableStateOf(tab) }
    var showPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTransmission by remember { mutableStateOf(false) }
    var showStartup by remember { mutableStateOf(settings.startupAnimation) }
    var logoRevealed by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()

    fun playTrack(track: com.twelvepts.cathode.model.AudioTrack) {
        player.play(library.tracks, track)
    }
    val editMetadata: MetadataEditor = { track, title, artist, album, tags, artwork, lyrics ->
        player.updateTrackMetadata(viewModel.updateMetadata(track, title, artist, album, tags, artwork, lyrics))
    }
    fun selectTab(next: CathodeTab, keepLibraryView: Boolean = false) {
        if (!keepLibraryView && (tab == CathodeTab.Library || next == CathodeTab.Library)) {
            settingsStore.update {
                it.copy(libraryCategory = LibraryCategory.SONGS, librarySort = LibrarySort.RECENT)
            }
        }
        previousTab = tab
        tab = next
        settingsStore.update { it.copy(lastTab = next.name) }
    }

    BackHandler(enabled = drawerState.isOpen && !showPlayer && !showSettings && !showTransmission) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = drawerState.isClosed && !showPlayer && !showSettings && !showTransmission && tab != CathodeTab.Home) {
        selectTab(CathodeTab.Home)
    }

    LaunchedEffect(settings.startupAnimation) {
        if (settings.startupAnimation) {
            showStartup = true
            logoRevealed = true
            delay(1_250)
            showStartup = false
        } else showStartup = false
    }
    LaunchedEffect(playback.connected, playback.isPlaying) {
        while (playback.connected) {
            player.refreshPosition()
            delay(500)
        }
    }

    CompositionLocalProvider(
        LocalPlayNext provides player::playNext,
        LocalAddToQueue provides player::addToQueue,
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ProfileDrawerContent(
                settings = settings,
                store = settingsStore,
                library = library,
                onSettings = { showSettings = true },
                onTransmission = { showTransmission = true },
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    if (playback.title.isNotEmpty()) MiniPlayer(
                        title = playback.title,
                        artist = playback.artist,
                        artworkUri = playback.artworkUri,
                        isPlaying = playback.isPlaying,
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        onOpen = { showPlayer = true },
                        onToggle = player::togglePlayPause,
                        compact = settings.compact,
                    )
                    NavigationBar(
                        containerColor = CathodePanel,
                        modifier = Modifier.height(if (settings.navigationStyle == NavigationStyle.COMPACT) 64.dp else 80.dp),
                    ) {
                        CathodeTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = item == tab,
                                onClick = { selectTab(item) },
                                icon = { Icon(item.icon, item.label) },
                                label = if (settings.navigationStyle == NavigationStyle.LABELED) ({ Text(item.label) }) else null,
                                alwaysShowLabel = settings.navigationStyle == NavigationStyle.LABELED,
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
                ArtworkBackdrop(playback.artworkUri, settings.animations)
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        val duration = if (settings.animations) 260 else 0
                        (slideInHorizontally(tween(duration)) { it / 8 * direction } + fadeIn(tween(duration)))
                            .togetherWith(slideOutHorizontally(tween(duration)) { -it / 8 * direction } + fadeOut(tween(duration)))
                    },
                    label = "main-tabs",
                ) { destination ->
                    when (destination) {
                        CathodeTab.Home -> HomeScreen(
                            state = library,
                            onRescan = viewModel::rescan,
                            onPlay = ::playTrack,
                            onEdit = editMetadata,
                            onReset = viewModel::resetMetadata,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddToPlaylist = viewModel::addToPlaylist,
                            onProfile = { scope.launch { drawerState.open() } },
                            onOpenLibrary = { category ->
                                settingsStore.update { it.copy(libraryCategory = category) }
                                selectTab(CathodeTab.Library, keepLibraryView = true)
                            },
                            currentTrackKey = playback.queue.getOrNull(playback.mediaItemIndex)?.mediaId,
                            currentIsPlaying = playback.isPlaying,
                            onTogglePlayback = player::togglePlayPause,
                            settings = settings,
                            store = settingsStore,
                        )
                        CathodeTab.Library -> LibraryScreen(
                            state = library,
                            requestPermission = requestPermission,
                            onRescan = viewModel::rescan,
                            onPlay = ::playTrack,
                            onEdit = editMetadata,
                            onReset = viewModel::resetMetadata,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onCreatePlaylist = viewModel::createPlaylist,
                            onCreatePlaylistWithTracks = viewModel::createPlaylistWithTracks,
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onUpdatePlaylist = viewModel::updatePlaylist,
                            onAddToPlaylist = viewModel::addToPlaylist,
                            onAddTracksToPlaylist = viewModel::addTracksToPlaylist,
                            onRemoveFromPlaylist = viewModel::removeFromPlaylist,
                            onMovePlaylistTrack = viewModel::movePlaylistTrack,
                            onBatchEdit = { tracks, artist, album, tags ->
                                viewModel.updateMetadataBatch(tracks, artist, album, tags).forEach(player::updateTrackMetadata)
                            },
                            onCreateSmartPlaylist = viewModel::createSmartPlaylist,
                            onUpdateSmartPlaylist = viewModel::updateSmartPlaylist,
                            onDeleteSmartPlaylist = viewModel::deleteSmartPlaylist,
                            onProfile = { scope.launch { drawerState.open() } },
                            settings = settings,
                            store = settingsStore,
                        )
                        CathodeTab.Discover -> AcquireScreen()
                    }
                }
            }
        }
    }

    }

    val overlayDuration = if (settings.animations) 280 else 0
    AnimatedVisibility(
        visible = showPlayer,
        enter = fadeIn(tween(overlayDuration)) + slideInVertically(tween(overlayDuration)) { it / 7 } + scaleIn(tween(overlayDuration), initialScale = .985f),
        exit = fadeOut(tween(overlayDuration)) + slideOutVertically(tween(overlayDuration)) { it / 7 } + scaleOut(tween(overlayDuration), targetScale = .985f),
        label = "now-playing-overlay",
    ) {
        NowPlayingScreen(playback, player, settings = settings, onDismiss = { showPlayer = false })
    }
    AnimatedVisibility(
        visible = showSettings,
        enter = fadeIn(tween(overlayDuration)) + slideInHorizontally(tween(overlayDuration)) { it / 6 },
        exit = fadeOut(tween(overlayDuration)) + slideOutHorizontally(tween(overlayDuration)) { it / 6 },
        label = "settings-overlay",
    ) {
        SettingsScreen(
            settings = settings,
            store = settingsStore,
            artworkUri = playback.artworkUri,
            playback = playback,
            player = player,
            library = library,
            onAnalyzeLossless = viewModel::analyzeLosslessLibrary,
            onReanalyzeLossless = viewModel::reanalyzeLosslessLibrary,
            onCancelAnalysis = viewModel::cancelLosslessAnalysis,
            onExportBackup = viewModel::exportBackup,
            onRestoreBackup = viewModel::restoreBackup,
            onClose = { showSettings = false },
        )
    }
    AnimatedVisibility(
        visible = showTransmission,
        enter = fadeIn(tween(overlayDuration)) + slideInHorizontally(tween(overlayDuration)) { it / 6 },
        exit = fadeOut(tween(overlayDuration)) + slideOutHorizontally(tween(overlayDuration)) { it / 6 },
        label = "transmission-overlay",
    ) {
        TransmissionLogScreen(library, playback.artworkUri, onClose = { showTransmission = false })
    }

    AnimatedVisibility(visible = showStartup, exit = fadeOut(tween(if (settings.animations) 450 else 0))) {
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
    compact: Boolean,
) {
    val view = LocalView.current
    Box(Modifier.fillMaxWidth().height(if (compact) 54.dp else 64.dp).background(CathodePanel).clickable(onClick = onOpen)) {
        AsyncImage(
            model = artworkUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(3.dp).alpha((.42f + CathodeGlowStrength * .28f).coerceIn(.42f, .68f)),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(CathodePanel.copy(alpha = .18f), CathodePanel.copy(alpha = .68f)))))
        LinearProgressIndicator(
            progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
            color = CathodeCyan,
            trackColor = CathodeDim.copy(alpha = .35f),
        )
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggle()
            }) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", tint = CathodeCyan)
            }
        }
    }
}

@Composable
private fun StartupReveal(revealed: Boolean) {
    val scale by animateFloatAsState(if (revealed) 1f else .72f, tween(700), label = "startup-logo-scale")
    val opacity by animateFloatAsState(if (revealed) 1f else 0f, tween(500), label = "startup-logo-opacity")
    Box(Modifier.fillMaxSize().background(CathodeBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.ic_cathode),
                "Cathode",
                Modifier.size(156.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = opacity },
            )
            Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp).alpha(opacity))
            Text("12PTS", color = CathodeMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp).alpha(opacity))
        }
    }
}
