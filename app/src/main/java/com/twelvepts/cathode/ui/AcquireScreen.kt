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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val MONOCHROME_URL = "https://monochrome.tf/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AcquireScreen() {
    val context = LocalContext.current
    val connectivity = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember { mutableStateOf(connectivity.isOnline()) }
    var retryKey by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadingProgress by remember { mutableIntStateOf(0) }

    DisposableEffect(connectivity) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = connectivity.isOnline() }
            override fun onLost(network: Network) { online = connectivity.isOnline() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        onDispose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Box(Modifier.fillMaxSize().background(CathodeBlack)) {
        if (!online) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.WifiOff, null, tint = CathodeCyan, modifier = Modifier.size(52.dp))
                Text("NO SIGNAL", color = CathodeCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                Text("Connect to Wi-Fi or mobile data to open Discover. Your downloaded music remains available offline.", color = CathodeMuted)
                Button(onClick = { online = connectivity.isOnline(); retryKey++ }) { Text("Retry connection") }
            }
        } else {
            AndroidView(
                factory = { webContext ->
                    WebView(webContext).apply {
                        webView = this
                        setBackgroundColor(android.graphics.Color.rgb(3, 9, 11))
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = false
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            mediaPlaybackRequiresUserGesture = true
                            setSupportMultipleWindows(false)
                            userAgentString = "$userAgentString Cathode/0.1"
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                return if (request.url.scheme == "https") false else {
                                    Toast.makeText(webContext, "Blocked non-HTTPS navigation", Toast.LENGTH_SHORT).show()
                                    true
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, progress: Int) { loadingProgress = progress }
                        }
                        setDownloadListener(CathodeDownloadListener(webContext))
                        loadUrl(MONOCHROME_URL)
                    }
                },
                update = { if (retryKey > 0 && it.url.isNullOrBlank()) it.loadUrl(MONOCHROME_URL) },
                modifier = Modifier.fillMaxSize(),
            )
            if (loadingProgress in 0..99) CircularProgressIndicator(
                progress = { loadingProgress / 100f },
                color = CathodeCyan,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply { stopLoading(); loadUrl("about:blank"); clearHistory(); removeAllViews(); destroy() }
            webView = null
        }
    }
}

private fun ConnectivityManager.isOnline(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private class CathodeDownloadListener(private val context: Context) : DownloadListener {
    override fun onDownloadStart(url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) {
        if (!url.startsWith("https://")) {
            Toast.makeText(context, "This download type requires Monochrome's native Android app", Toast.LENGTH_LONG).show()
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
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Monochrome/$filename")
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
            .onFailure { Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}
