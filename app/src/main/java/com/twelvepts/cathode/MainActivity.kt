package com.twelvepts.cathode

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.twelvepts.cathode.playback.PlayerConnection
import com.twelvepts.cathode.data.CathodeDiagnostics
import com.twelvepts.cathode.ui.CathodeApp
import com.twelvepts.cathode.ui.CathodeSettingsStore
import com.twelvepts.cathode.ui.CathodeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<CathodeViewModel>()
    private lateinit var playerConnection: PlayerConnection
    private lateinit var settingsStore: CathodeSettingsStore
    private var downloadReceiverRegistered = false
    private var libraryRefreshJob: Job? = null
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            libraryRefreshJob?.cancel()
            libraryRefreshJob = lifecycleScope.launch {
                delay(650)
                viewModel.rescan()
            }
        }
    }
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val preferences = getSharedPreferences("cathode_downloads", Context.MODE_PRIVATE)
            val active = preferences.getStringSet("active_ids", emptySet()).orEmpty()
            if (id.toString() !in active) return

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var title = "Download"
            var localUri: String? = null
            var reason = 0
            val status = manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: title
                    localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                } else null
            }
            preferences.edit()
                .putStringSet("active_ids", active - id.toString())
                .putString("last_title", title)
                .putInt("last_status", status ?: DownloadManager.STATUS_FAILED)
                .putInt("last_reason", reason)
                .apply()
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val quality = localUri?.let(::classifyDownloadedAudio) ?: "UNKNOWN"
                preferences.edit()
                    .putStringSet("completed_ids", preferences.getStringSet("completed_ids", emptySet()).orEmpty() + id.toString())
                    .putString("title_" + id, title)
                    .putString("quality_" + id, quality)
                    .apply()
                if (quality == "LOSSY") notifyLossyDownload(id, title)
                localUri?.let(Uri::parse)?.path?.let { path ->
                    MediaScannerConnection.scanFile(this@MainActivity, arrayOf(path), null, null)
                }
                lifecycleScope.launch {
                    delay(1_200)
                    viewModel.rescan()
                }
            } else {
                CathodeDiagnostics.record(this@MainActivity, "Download", "$title failed (reason $reason)")
            }
        }
    }

    private fun classifyDownloadedAudio(uriText: String): String = runCatching {
        val uri = Uri.parse(uriText)
        val extractor = MediaExtractor()
        try {
            if (uri.scheme == "file") extractor.setDataSource(uri.path!!) else extractor.setDataSource(this, uri, null)
            val mime = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.getString(MediaFormat.KEY_MIME).orEmpty().lowercase()
            when {
                mime.contains("flac") || mime.contains("alac") || mime.contains("raw") -> "LOSSLESS"
                mime.contains("mpeg") || mime.contains("aac") || mime.contains("opus") || mime.contains("vorbis") -> "LOSSY"
                else -> "UNKNOWN"
            }
        } finally { extractor.release() }
    }.getOrDefault("UNKNOWN")

    private fun notifyLossyDownload(id: Long, title: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "cathode_quality"
        manager.createNotificationChannel(NotificationChannel(channelId, "Audio quality alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_cathode)
            .setContentTitle("Downloaded audio is not lossless")
            .setContentText("$title uses a lossy codec despite its filename or source listing.")
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify((id xor (id ushr 32)).toInt(), notification)
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setPermission(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CathodeDiagnostics.record(this, "Crash", "Uncaught error on ${thread.name}", throwable)
            previousCrashHandler?.uncaughtException(thread, throwable)
        }
        enableEdgeToEdge()
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        playerConnection = PlayerConnection(this)
        settingsStore = CathodeSettingsStore(this)
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        downloadReceiverRegistered = true
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver,
        )

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermission(granted)
        if (!granted) audioPermissionLauncher.launch(permission)

        setContent {
            val settings by settingsStore.state.collectAsStateWithLifecycle()
            CathodeTheme(settings) {
                val detectedDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(detectedDensity.density * settings.layoutScale, detectedDensity.fontScale),
                ) {
                    CathodeApp(
                        viewModel = viewModel,
                        player = playerConnection,
                        requestPermission = { audioPermissionLauncher.launch(permission) },
                        settings = settings,
                        settingsStore = settingsStore,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.rescan()
    }

    override fun onDestroy() {
        if (downloadReceiverRegistered) unregisterReceiver(downloadReceiver)
        contentResolver.unregisterContentObserver(mediaObserver)
        libraryRefreshJob?.cancel()
        playerConnection.release()
        super.onDestroy()
    }
}
