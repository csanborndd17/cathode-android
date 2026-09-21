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
    fun selectTab(next: CathodeTab) {
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
                            onEdit = viewModel::updateMetadata,
                            onReset = viewModel::resetMetadata,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddToPlaylist = viewModel::addToPlaylist,
                            onProfile = { scope.launch { drawerState.open() } },
                            onOpenLibrary = { category ->
                                settingsStore.update { it.copy(libraryCategory = category) }
                                selectTab(CathodeTab.Library)
                            },
                            currentTrackKey = playback.queue.getOrNull(playback.mediaItemIndex)?.mediaId,
                            settings = settings,
                            store = settingsStore,
                        )
                        CathodeTab.Library -> LibraryScreen(
                            state = library,
                            requestPermission = requestPermission,
                            onRescan = viewModel::rescan,
                            onPlay = ::playTrack,
                            onEdit = viewModel::updateMetadata,
                            onReset = viewModel::resetMetadata,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onCreatePlaylist = viewModel::createPlaylist,
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onAddToPlaylist = viewModel::addToPlaylist,
                            onRemoveFromPlaylist = viewModel::removeFromPlaylist,
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

    if (showPlayer) NowPlayingScreen(playback, player, animations = settings.animations, onDismiss = { showPlayer = false })
    if (showSettings) SettingsScreen(settings, settingsStore, playback.artworkUri, onClose = { showSettings = false })
    if (showTransmission) TransmissionLogScreen(library, playback.artworkUri, onClose = { showTransmission = false })

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
            modifier = Modifier.fillMaxSize().blur(8.dp).alpha((.16f + CathodeGlowStrength * .34f).coerceIn(.16f, .5f)),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(CathodePanel.copy(alpha = .45f), CathodePanel.copy(alpha = .92f)))))
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
