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
import androidx.core.content.ContextCompat
import com.twelvepts.cathode.playback.PlayerConnection
import com.twelvepts.cathode.ui.CathodeApp
import com.twelvepts.cathode.ui.CathodeTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<CathodeViewModel>()
    private lateinit var playerConnection: PlayerConnection

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setPermission(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playerConnection = PlayerConnection(this)

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermission(granted)
        if (!granted) audioPermissionLauncher.launch(permission)

        setContent {
            CathodeTheme {
                CathodeApp(
                    viewModel = viewModel,
                    player = playerConnection,
                    requestPermission = { audioPermissionLauncher.launch(permission) },
                )
            }
        }
    }

    override fun onDestroy() {
        playerConnection.release()
        super.onDestroy()
    }
}
