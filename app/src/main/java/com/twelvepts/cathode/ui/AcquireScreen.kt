package com.twelvepts.cathode.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val MONOCHROME_URL = "https://monochrome.samidy.com/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AcquireScreen(onDownloadStarted: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadingProgress by remember { mutableIntStateOf(0) }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Box(Modifier.fillMaxSize().background(CathodeBlack)) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
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
                            val target = request.url
                            return if (target.scheme == "https") false else {
                                Toast.makeText(context, "Blocked non-HTTPS navigation", Toast.LENGTH_SHORT).show()
                                true
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadingProgress = newProgress
                        }
                    }
                    setDownloadListener(CathodeDownloadListener(context, onDownloadStarted))
                    loadUrl(MONOCHROME_URL)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (loadingProgress in 0..99) {
            CircularProgressIndicator(
                progress = { loadingProgress / 100f },
                color = CathodeCyan,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
}

private class CathodeDownloadListener(
    private val context: Context,
    private val onDownloadStarted: () -> Unit,
) : DownloadListener {
    override fun onDownloadStart(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long,
    ) {
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
            .onSuccess {
                Toast.makeText(context, "Download started: $filename", Toast.LENGTH_SHORT).show()
                onDownloadStarted()
            }
            .onFailure { Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}
