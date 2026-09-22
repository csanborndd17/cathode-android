package com.twelvepts.cathode.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.twelvepts.cathode.BuildConfig
import com.twelvepts.cathode.data.SpotifyPlaylistResolver
import com.twelvepts.cathode.data.YouTubePlaylistResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class DownloadSignal(
    val id: Long,
    val title: String,
    val status: Int,
    val progress: Float?,
    val quality: String? = null,
)

private data class DownloadSnapshot(
    val active: List<DownloadSignal> = emptyList(),
    val completed: List<DownloadSignal> = emptyList(),
    val failedTitle: String? = null,
    val failedReason: Int = 0,
)

private data class DiscoverSource(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val url: String,
    val badge: String,
    val downloadFolder: String,
    val icon: ImageVector,
)

private data class ImportedTrack(val artist: String, val title: String) {
    val query: String get() = listOf(artist, title).filter(String::isNotBlank).joinToString(" ")
}

private val discoverSources = listOf(
    DiscoverSource(
        "monochrome", "Monochrome", "Primary instance · direct lossless downloads",
        "Search a large catalog and download available high-resolution FLAC files without a Cathode account.",
        "https://monochrome.tf/", "NO ACCOUNT · FLAC", "Monochrome", Icons.Default.MusicNote,
    ),
    DiscoverSource(
        "monochrome-mirror", "Monochrome Mirror", "Official failover · lossless.wtf",
        "Use the project’s official mirror when the primary Monochrome instance is unavailable.",
        "https://lossless.wtf/", "OFFICIAL FAILOVER", "Monochrome", Icons.Default.Language,
    ),
    DiscoverSource(
        "archive", "Internet Archive", "Open audio archive · format varies",
        "Browse downloadable audio. FLAC is available only when the uploader supplied it; verify each item’s rights and file list.",
        "https://archive.org/details/audio", "NO ACCOUNT · SOME FLAC", "Cathode/Internet Archive", Icons.Default.Archive,
    ),
    DiscoverSource(
        "bandcamp", "Bandcamp", "Artist storefronts · purchases and free releases",
        "Support artists directly. Purchases can be downloaded as FLAC; free and name-your-price availability depends on the artist.",
        "https://bandcamp.com/discover", "FLAC AFTER CHECKOUT", "Cathode/Bandcamp", Icons.Default.ShoppingBag,
    ),
    DiscoverSource(
        "commons", "Wikimedia Commons", "Freely licensed media · smaller FLAC catalog",
        "Browse freely licensed audio without an account. Some files are FLAC, while many are Ogg or other open formats.",
        "https://commons.wikimedia.org/wiki/Category:FLAC_files", "NO ACCOUNT · FREE LICENSES", "Cathode/Wikimedia", Icons.Default.Language,
    ),
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AcquireScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spotify = remember { SpotifyPlaylistResolver(context.applicationContext) }
    val youtube = remember { YouTubePlaylistResolver(context.applicationContext) }
    val connectivity = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember { mutableStateOf(connectivity.isOnline()) }
    var selectedSource by remember { mutableStateOf<DiscoverSource?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var downloads by remember { mutableStateOf(DownloadSnapshot()) }
    var showPlaylistImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importedTracks by remember { mutableStateOf<List<ImportedTrack>>(emptyList()) }
    var playlistLinkNotice by remember { mutableStateOf<String?>(null) }
    var resolvingPlaylist by remember { mutableStateOf(false) }
    var importSource by remember { mutableStateOf(discoverSources.first()) }
    var completedImports by remember { mutableStateOf<Set<String>>(emptySet()) }
    var skippedImports by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQueue by remember { mutableStateOf<List<ImportedTrack>>(emptyList()) }
    var searchIndex by remember { mutableIntStateOf(0) }
    var showSearchQueue by remember { mutableStateOf(false) }
    val currentSearch = searchQueue.getOrNull(searchIndex)
    fun loadQueuedSearch(index: Int, markCurrentSearched: Boolean = false, skipCurrent: Boolean = false) {
        if (markCurrentSearched) currentSearch?.query?.let { completedImports = completedImports + it }
        if (skipCurrent) currentSearch?.query?.let { skippedImports = skippedImports + it }
        if (searchQueue.isEmpty()) return
        searchIndex = index.coerceIn(0, searchQueue.lastIndex)
        val track = searchQueue[searchIndex]
        webView?.loadUrl(importSource.searchUrl(track.query))
    }
    fun startSearchSession(tracks: List<ImportedTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        searchQueue = tracks
        searchIndex = startIndex.coerceIn(0, tracks.lastIndex)
        showPlaylistImport = false
        selectedSource = importSource
    }
    fun closeSource() {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        loadingProgress = 0
        loadError = null
        selectedSource = null
    }

    DisposableEffect(connectivity) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = connectivity.isOnline() }
            override fun onLost(network: Network) { online = connectivity.isOnline() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        onDispose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }
    LaunchedEffect(Unit) {
        while (true) {
            downloads = readDownloadSnapshot(context)
            delay(750)
        }
    }

    BackHandler(enabled = selectedSource != null) {
        if (webView?.canGoBack() == true) webView?.goBack() else closeSource()
    }

    Box(Modifier.fillMaxSize().background(CathodeBlack)) {
        if (selectedSource == null) {
            DiscoverHub(
                online = online,
                downloads = downloads,
                onOpen = { selectedSource = it },
                onCancelDownload = { id ->
                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
                    val preferences = context.getSharedPreferences("cathode_downloads", Context.MODE_PRIVATE)
                    preferences.edit().putStringSet("active_ids", preferences.getStringSet("active_ids", emptySet()).orEmpty() - id.toString()).apply()
                    downloads = readDownloadSnapshot(context)
                },
                onDismissFailure = {
                    context.getSharedPreferences("cathode_downloads", Context.MODE_PRIVATE).edit()
                        .remove("last_status").remove("last_title").remove("last_reason").apply()
                    downloads = readDownloadSnapshot(context)
                },
                onDismissCompleted = { id ->
                    val preferences = context.getSharedPreferences("cathode_downloads", Context.MODE_PRIVATE)
                    preferences.edit()
                        .putStringSet("completed_ids", preferences.getStringSet("completed_ids", emptySet()).orEmpty() - id.toString())
                        .remove("title_" + id)
                        .remove("quality_" + id)
                        .apply()
                    downloads = readDownloadSnapshot(context)
                },
                onImportPlaylist = { showPlaylistImport = true },
            )
        } else {
            val source = selectedSource!!
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().background(CathodeBlack.copy(alpha = .88f)).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::closeSource) {
                        Icon(Icons.Default.ArrowBack, "Back to Discover sources", tint = CathodeCyan)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(source.name, fontWeight = FontWeight.Bold)
                        Text(source.badge, color = CathodeCyan, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { webView?.loadUrl(source.url) }, enabled = online) { Text("Home") }
                }
                if (currentSearch != null && source == importSource) {
                    Surface(color = CathodePanel.copy(alpha = .96f)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("SEARCH QUEUE ${searchIndex + 1}/${searchQueue.size}", color = CathodeCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(currentSearch.query, maxLines = 1)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { loadQueuedSearch(searchIndex - 1) }, enabled = searchIndex > 0) { Text("Previous") }
                                TextButton(onClick = { showSearchQueue = true }) { Text("Queue") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { loadQueuedSearch(searchIndex + 1, skipCurrent = true) }) { Text("Skip") }
                                Button(onClick = { loadQueuedSearch(searchIndex + 1, markCurrentSearched = true) }) { Text(if (searchIndex == searchQueue.lastIndex) "Finish" else "Next") }
                            }
                        }
                    }
                }
                if (!online) {
                    OfflinePanel {
                        online = connectivity.isOnline()
                        retryKey++
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { webContext ->
                                WebView(webContext).apply {
                                    webView = this
                                    setBackgroundColor(android.graphics.Color.rgb(3, 9, 11))
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = false
                                        allowFileAccess = false
                                        allowContentAccess = false
                                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                        mediaPlaybackRequiresUserGesture = true
                                        setSupportMultipleWindows(false)
                                        userAgentString = userAgentString + " Cathode/" + BuildConfig.VERSION_NAME
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            loadError = null
                                            loadingProgress = maxOf(loadingProgress, 1)
                                        }
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            return if (request.url.scheme == "https") false else {
                                                Toast.makeText(webContext, "Cathode blocked non-HTTPS navigation", Toast.LENGTH_SHORT).show()
                                                true
                                            }
                                        }
                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                            if (request.isForMainFrame) {
                                                loadError = error.description?.toString() ?: "The source could not be reached."
                                                loadingProgress = 100
                                            }
                                        }
                                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                                loadError = "The source returned HTTP " + errorResponse.statusCode + "."
                                                loadingProgress = 100
                                            }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, progress: Int) { loadingProgress = progress }
                                    }
                                    setDownloadListener(CathodeDownloadListener(webContext, source.downloadFolder))
                                    loadUrl(if (currentSearch != null && source == importSource) source.searchUrl(currentSearch.query) else source.url)
                                }
                            },
                            update = { if (retryKey > 0 && it.url.isNullOrBlank()) it.loadUrl(source.url) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (loadingProgress in 1..99 && loadError == null) CircularProgressIndicator(
                            progress = { loadingProgress / 100f },
                            color = CathodeCyan,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                        )
                        loadError?.let { message ->
                            Surface(
                                color = CathodePanel.copy(alpha = .96f),
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.align(Alignment.Center).padding(26.dp),
                            ) {
                                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("SOURCE UNAVAILABLE", color = CathodeCyan, fontWeight = FontWeight.Bold)
                                    Text(message, color = CathodeMuted, modifier = Modifier.padding(vertical = 10.dp))
                                    Button(onClick = {
                                        loadError = null
                                        loadingProgress = 1
                                        webView?.loadUrl(source.url)
                                    }) { Text("Retry") }
                                    TextButton(onClick = ::closeSource) { Text("Back to sources") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistImport) AlertDialog(
        onDismissRequest = { showPlaylistImport = false },
        title = { Text("Playlist converter") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste a track list, or an authorized playlist link once its provider is connected. Cathode reads names only; it never downloads from Spotify or YouTube.", color = CathodeMuted)
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("Playlist text") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    val link = importText.trim().takeIf { it.startsWith("https://") }
                    if (link != null && "spotify.com/" in link) {
                        importedTracks = emptyList()
                        if (!spotify.isConnected) {
                            playlistLinkNotice = "Connect Spotify in Settings → Advanced → Spotify playlist import, then try again."
                        } else {
                            resolvingPlaylist = true
                            playlistLinkNotice = "Reading playlist from Spotify…"
                            scope.launch {
                                spotify.resolve(link).fold(
                                    onSuccess = { tracks ->
                                        importedTracks = tracks.map { ImportedTrack(it.artist, it.title) }
                                        playlistLinkNotice = if (tracks.isEmpty()) "Spotify returned no readable tracks." else "Loaded ${tracks.size} tracks from Spotify."
                                    },
                                    onFailure = { playlistLinkNotice = it.message ?: "Spotify playlist lookup failed." },
                                )
                                resolvingPlaylist = false
                            }
                        }
                    } else if (link != null && ("youtube.com/" in link || "youtu.be/" in link)) {
                        importedTracks = emptyList()
                        if (!youtube.isConfigured) {
                            playlistLinkNotice = "Add a YouTube Data API key in Settings → Advanced → YouTube playlist import, then try again."
                        } else {
                            resolvingPlaylist = true
                            playlistLinkNotice = "Reading playlist from YouTube…"
                            scope.launch {
                                youtube.resolve(link).fold(
                                    onSuccess = { tracks ->
                                        importedTracks = tracks.map { ImportedTrack(it.artist, it.title) }
                                        playlistLinkNotice = if (tracks.isEmpty()) "YouTube returned no readable public tracks." else "Loaded ${tracks.size} tracks from YouTube."
                                    },
                                    onFailure = { playlistLinkNotice = it.message ?: "YouTube playlist lookup failed." },
                                )
                                resolvingPlaylist = false
                            }
                        }
                    } else if (link != null) {
                        importedTracks = emptyList()
                        playlistLinkNotice = "This playlist provider is not connected. Export it as Artist - Title lines for now."
                    } else {
                        playlistLinkNotice = null
                        importedTracks = parsePlaylistText(importText)
                    }
                }, enabled = importText.isNotBlank() && !resolvingPlaylist) {
                    Text(if (resolvingPlaylist) "Reading Spotify…" else "Parse tracks")
                }
                playlistLinkNotice?.let {
                    Surface(color = CathodePanel, shape = MaterialTheme.shapes.medium) {
                        Text(it, color = CathodeMuted, modifier = Modifier.padding(12.dp))
                    }
                }
                if (importedTracks.isNotEmpty()) {
                    Text("${completedImports.size}/${importedTracks.size} searches opened", color = CathodeCyan)
                    TextButton(onClick = {
                        context.getSharedPreferences("cathode_imports", Context.MODE_PRIVATE).edit()
                            .putString("draft_text", importText)
                            .putString("draft_tracks", encodeImportedTracks(importedTracks))
                            .putString("draft_source", importSource.id)
                            .putStringSet("draft_completed", completedImports)
                            .putStringSet("draft_skipped", skippedImports)
                            .apply()
                        Toast.makeText(context, "Conversion session saved", Toast.LENGTH_SHORT).show()
                    }) { Text("Save session") }
                    TextButton(onClick = {
                        val preferences = context.getSharedPreferences("cathode_imports", Context.MODE_PRIVATE)
                        importText = preferences.getString("draft_text", "").orEmpty()
                        importedTracks = decodeImportedTracks(preferences.getString("draft_tracks", null)).ifEmpty { parsePlaylistText(importText) }
                        importSource = discoverSources.firstOrNull { it.id == preferences.getString("draft_source", "") } ?: discoverSources.first()
                        completedImports = preferences.getStringSet("draft_completed", emptySet()).orEmpty()
                        skippedImports = preferences.getStringSet("draft_skipped", emptySet()).orEmpty()
                    }) { Text("Resume saved session") }
                }
                Text("Search source", color = CathodeCyan, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(discoverSources.size) { index ->
                        val source = discoverSources[index]
                        FilterChip(selected = source == importSource, onClick = { importSource = source }, label = { Text(source.name) })
                    }
                }
                if (importedTracks.isNotEmpty()) Button(
                    onClick = { startSearchSession(importedTracks, importedTracks.indexOfFirst { it.query !in completedImports }.coerceAtLeast(0)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Search all (${importedTracks.size})") }
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 300.dp)) {
                    itemsIndexed(importedTracks, key = { _, track -> track.query }) { index, track ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}. ${track.query}",
                            Modifier.weight(1f),
                            maxLines = 2,
                            color = if (track.query in completedImports || track.query in skippedImports) CathodeMuted else CathodeText,
                        )
                        TextButton(onClick = {
                            startSearchSession(importedTracks, index)
                        }) { Text("Search") }
                        IconButton(onClick = {
                            importedTracks = importedTracks.filterNot { it == track }
                            completedImports = completedImports - track.query
                        }) { Icon(Icons.Default.Close, "Remove imported track", tint = CathodeMuted) }
                    }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showPlaylistImport = false }) { Text("Close") } },
    )

    if (showSearchQueue) AlertDialog(
        onDismissRequest = { showSearchQueue = false },
        title = { Text("Search queue") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                itemsIndexed(searchQueue, key = { _, track -> track.query }) { index, track ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            loadQueuedSearch(index)
                            showSearchQueue = false
                        }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}", color = if (index == searchIndex) CathodeCyan else CathodeMuted, modifier = Modifier.width(42.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title.ifBlank { track.query }, maxLines = 1)
                            if (track.artist.isNotBlank()) Text(track.artist, color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        Text(
                            when (track.query) {
                                in completedImports -> "DONE"
                                in skippedImports -> "SKIPPED"
                                else -> ""
                            },
                            color = CathodeMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSearchQueue = false }) { Text("Done") } },
    )

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply { stopLoading(); loadUrl("about:blank"); clearHistory(); removeAllViews(); destroy() }
            webView = null
        }
    }
}

private fun DiscoverSource.searchUrl(query: String): String {
    val encoded = Uri.encode(query)
    return when (id) {
        "monochrome", "monochrome-mirror" -> url.trimEnd('/') + "/search/$encoded"
        "archive" -> "https://archive.org/advancedsearch.php?q=$encoded"
        "bandcamp" -> "https://bandcamp.com/search?q=$encoded"
        "commons" -> "https://commons.wikimedia.org/wiki/Special:MediaSearch?type=audio&search=$encoded"
        else -> url
    }
}

@Composable
private fun DiscoverHub(
    online: Boolean,
    downloads: DownloadSnapshot,
    onOpen: (DiscoverSource) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onDismissFailure: () -> Unit,
    onDismissCompleted: (Long) -> Unit,
    onImportPlaylist: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Column(Modifier.padding(top = 14.dp, bottom = 5.dp)) {
                Text("DISCOVER", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Choose a source", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Downloads stay local and return to your Cathode library.", color = CathodeMuted)
            }
        }
        if (!online) item {
            Surface(color = CathodeCyan.copy(alpha = .12f), shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, null, tint = CathodeCyan)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("NO SIGNAL", color = CathodeCyan, fontWeight = FontWeight.Bold)
                        Text("Connect to Wi-Fi or mobile data before opening a source.", color = CathodeMuted)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onImportPlaylist)) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlaylistAdd, null, tint = CathodeCyan, modifier = Modifier.size(34.dp))
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("CONVERT A PLAYLIST", color = CathodeCyan, fontWeight = FontWeight.Bold)
                        Text("Paste a track list, review it, then search each song through your selected source.", color = CathodeMuted)
                    }
                }
            }
        }
        downloads.active.forEach { download ->
            item(key = "download-" + download.id) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(start = 15.dp, end = 7.dp, top = 10.dp, bottom = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("DOWNLOADING", color = CathodeCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(download.title, maxLines = 1)
                            }
                            IconButton(onClick = { onCancelDownload(download.id) }) {
                                Icon(Icons.Default.Close, "Cancel download", tint = CathodeMuted)
                            }
                        }
                        if (download.progress != null) LinearProgressIndicator(
                            progress = { download.progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = CathodeCyan,
                        ) else LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = CathodeCyan)
                    }
                }
            }
        }
        downloads.completed.forEach { download ->
            item(key = "installed-" + download.id) {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 15.dp, end = 7.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("INSTALLED", color = CathodeCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(download.title, maxLines = 1)
                            Text("Available in your local library", color = CathodeMuted, style = MaterialTheme.typography.labelSmall)
                            Text(download.quality ?: "QUALITY UNKNOWN", color = if (download.quality == "LOSSY") MaterialTheme.colorScheme.error else CathodeCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onDismissCompleted(download.id) }) {
                            Icon(Icons.Default.Close, "Dismiss completed download", tint = CathodeMuted)
                        }
                    }
                }
            }
        }
        downloads.failedTitle?.let { title ->
            item(key = "download-failed") {
                Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .82f), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 5.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("DOWNLOAD FAILED", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                            Text(title, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 1)
                            Text(downloadFailureLabel(downloads.failedReason), color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = onDismissFailure) { Icon(Icons.Default.Close, "Dismiss failed download") }
                    }
                }
            }
        }
        discoverSources.forEachIndexed { index, source ->
            item(key = source.id) { SourceCard(source, index, online, onOpen) }
        }
        item {
            Text(
                "Cathode does not bypass subscriptions, DRM, or licensing. Download only music you own or are permitted to use.",
                color = CathodeMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 5.dp, bottom = 24.dp),
            )
        }
    }
}

private fun parsePlaylistText(input: String): List<ImportedTrack> = input.lineSequence()
    .map(String::trim)
    .filter { it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://") }
    .map { line ->
        val cleaned = line.replace(Regex("^\\s*\\d+[.)]\\s*"), "").trim()
        val separator = listOf(" — ", " – ", " - ", " | ", "\t").firstOrNull(cleaned::contains)
        if (separator == null) ImportedTrack("", cleaned)
        else {
            val parts = cleaned.split(separator, limit = 2)
            ImportedTrack(parts[0].trim(), parts.getOrElse(1) { "" }.trim())
        }
    }
    .filter { it.query.isNotBlank() }
    .distinctBy { it.query.lowercase() }
    .toList()

private fun encodeImportedTracks(tracks: List<ImportedTrack>): String = JSONArray().apply {
    tracks.forEach { track -> put(JSONObject().put("artist", track.artist).put("title", track.title)) }
}.toString()

private fun decodeImportedTracks(value: String?): List<ImportedTrack> = runCatching {
    val array = JSONArray(value ?: return emptyList())
    buildList(array.length()) {
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val track = ImportedTrack(row.optString("artist"), row.optString("title"))
            if (track.query.isNotBlank()) add(track)
        }
    }
}.getOrDefault(emptyList())

@Composable
private fun SourceCard(source: DiscoverSource, index: Int, online: Boolean, onOpen: (DiscoverSource) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = online) { onOpen(source) }) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    listOf(
                        CathodeCyan.copy(alpha = .06f + (index % 3) * .025f),
                        CathodePanel,
                        CathodeBlack.copy(alpha = .72f),
                    ),
                ),
            ),
        ) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = CathodeCyan.copy(alpha = .14f), shape = MaterialTheme.shapes.medium) {
                    Icon(source.icon, null, tint = CathodeCyan, modifier = Modifier.padding(13.dp).size(27.dp))
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(source.subtitle, color = CathodeCyan, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(5.dp))
                    Text(source.description, color = CathodeMuted, style = MaterialTheme.typography.bodySmall)
                    Text(source.badge, color = if (online) CathodeText else CathodeDim, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun OfflinePanel(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.WifiOff, null, tint = CathodeCyan, modifier = Modifier.size(52.dp))
        Text("NO SIGNAL", color = CathodeCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Text("Connect to Wi-Fi or mobile data to open this source. Your downloaded music remains available offline.", color = CathodeMuted, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRetry) { Text("Retry connection") }
    }
}

private fun ConnectivityManager.isOnline(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private class CathodeDownloadListener(private val context: Context, private val folder: String) : DownloadListener {
    override fun onDownloadStart(url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) {
        if (!url.startsWith("https://")) {
            Toast.makeText(context, "Cathode only accepts HTTPS downloads", Toast.LENGTH_LONG).show()
            return
        }
        val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            setTitle(filename)
            setDescription("Cathode // acquiring local audio")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$folder/$filename")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { manager.enqueue(request) }
            .onSuccess { id ->
                val preferences = context.getSharedPreferences("cathode_downloads", Context.MODE_PRIVATE)
                preferences.edit()
                    .putStringSet("active_ids", preferences.getStringSet("active_ids", emptySet()).orEmpty() + id.toString())
                    .putString("title_" + id, filename)
                    .apply()
                Toast.makeText(context, "Download started: $filename", Toast.LENGTH_SHORT).show()
            }
            .onFailure { error -> Toast.makeText(context, "Download failed: " + error.message, Toast.LENGTH_LONG).show() }
    }
}

private fun readDownloadSnapshot(context: Context): DownloadSnapshot {
    val preferences = context.getSharedPreferences("cathode_downloads", Context.MODE_PRIVATE)
    val ids = preferences.getStringSet("active_ids", emptySet()).orEmpty().mapNotNull(String::toLongOrNull)
    val completedIds = preferences.getStringSet("completed_ids", emptySet()).orEmpty().mapNotNull(String::toLongOrNull)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val active = ids.mapNotNull { id ->
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                    ?: preferences.getString("title_" + id, "Download").orEmpty()
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                DownloadSignal(id, title, status, if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null)
            }
        }.getOrNull()
    }
    val lastStatus = preferences.getInt("last_status", 0)
    return DownloadSnapshot(
        active = active,
        completed = completedIds.map { id ->
            DownloadSignal(
                id = id,
                title = preferences.getString("title_" + id, "Downloaded track").orEmpty(),
                status = DownloadManager.STATUS_SUCCESSFUL,
                progress = 1f,
                quality = preferences.getString("quality_" + id, null),
            )
        },
        failedTitle = preferences.getString("last_title", null).takeIf { lastStatus == DownloadManager.STATUS_FAILED },
        failedReason = preferences.getInt("last_reason", 0),
    )
}

private fun downloadFailureLabel(reason: Int): String = when (reason) {
    DownloadManager.ERROR_CANNOT_RESUME -> "The source would not resume the transfer."
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Download storage is unavailable."
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A file with this name already exists."
    DownloadManager.ERROR_FILE_ERROR -> "Android could not write the file."
    DownloadManager.ERROR_HTTP_DATA_ERROR -> "The source returned incomplete data."
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "There is not enough free storage."
    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The source redirected too many times."
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The source returned an unsupported response."
    else -> "Android DownloadManager reported error " + reason + "."
}
