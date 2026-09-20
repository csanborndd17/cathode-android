package com.twelvepts.cathode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twelvepts.cathode.playback.PlayerConnection
import com.twelvepts.cathode.ui.CathodeApp
import com.twelvepts.cathode.ui.CathodeSettingsStore
import com.twelvepts.cathode.ui.CathodeTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<CathodeViewModel>()
    private lateinit var playerConnection: PlayerConnection
    private lateinit var settingsStore: CathodeSettingsStore

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setPermission(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playerConnection = PlayerConnection(this)
        settingsStore = CathodeSettingsStore(this)

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

    override fun onDestroy() {
        playerConnection.release()
        super.onDestroy()
    }
}
