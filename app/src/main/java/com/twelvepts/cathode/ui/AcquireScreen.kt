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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.WifiOff
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
    val connectivity = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember { mutableStateOf(connectivity.isOnline()) }
    var selectedSource by remember { mutableStateOf<DiscoverSource?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadingProgress by remember { mutableIntStateOf(0) }

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

    BackHandler(enabled = selectedSource != null) {
        if (webView?.canGoBack() == true) webView?.goBack() else {
            loadingProgress = 0
            selectedSource = null
        }
    }

    Box(Modifier.fillMaxSize().background(CathodeBlack)) {
        if (selectedSource == null) {
            DiscoverHub(online = online, onOpen = { selectedSource = it })
        } else {
            val source = selectedSource!!
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().background(CathodeBlack.copy(alpha = .88f)).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        loadingProgress = 0
                        selectedSource = null
                    }) { Icon(Icons.Default.ArrowBack, "Back to Discover sources", tint = CathodeCyan) }
                    Column(Modifier.weight(1f)) {
                        Text(source.name, fontWeight = FontWeight.Bold)
                        Text(source.badge, color = CathodeCyan, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { webView?.loadUrl(source.url) }, enabled = online) { Text("Home") }
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
                                        userAgentString = "$userAgentString Cathode/0.7.4"
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            return if (request.url.scheme == "https") false else {
                                                Toast.makeText(webContext, "Cathode blocked non-HTTPS navigation", Toast.LENGTH_SHORT).show()
                                                true
                                            }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, progress: Int) { loadingProgress = progress }
                                    }
                                    setDownloadListener(CathodeDownloadListener(webContext, source.downloadFolder))
                                    loadUrl(source.url)
                                }
                            },
                            update = { if (retryKey > 0 && it.url.isNullOrBlank()) it.loadUrl(source.url) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (loadingProgress in 0..99) CircularProgressIndicator(
                            progress = { loadingProgress / 100f },
                            color = CathodeCyan,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(selectedSource) {
        onDispose {
            webView?.apply { stopLoading(); loadUrl("about:blank"); clearHistory(); removeAllViews(); destroy() }
            webView = null
        }
    }
}

@Composable
private fun DiscoverHub(online: Boolean, onOpen: (DiscoverSource) -> Unit) {
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
                preferences.edit().putStringSet("active_ids", preferences.getStringSet("active_ids", emptySet()).orEmpty() + id.toString()).apply()
                Toast.makeText(context, "Download started: $filename", Toast.LENGTH_SHORT).show()
            }
            .onFailure { error -> Toast.makeText(context, "Download failed: " + error.message, Toast.LENGTH_LONG).show() }
    }
}
