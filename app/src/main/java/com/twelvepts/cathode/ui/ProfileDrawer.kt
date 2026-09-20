package com.twelvepts.cathode.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twelvepts.cathode.LibraryState
import java.util.Calendar

@Composable
fun ProfileDrawerContent(
    settings: CathodeSettings,
    store: CathodeSettingsStore,
    library: LibraryState,
    onSettings: () -> Unit,
    onTransmission: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val transmissionIsNew = settings.transmissionLogSeenYear < currentYear
    var editing by remember { mutableStateOf(false) }
    var name by remember(settings.profileName) { mutableStateOf(settings.profileName) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            store.update { it.copy(profileImageUri = uri.toString()) }
        }
    }
    ModalDrawerSheet(drawerContainerColor = CathodePanel) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            AsyncImage(
                model = settings.profileImageUri,
                contentDescription = "Profile picture",
                modifier = Modifier.size(84.dp).clip(CircleShape).background(CathodeDim).clickable { picker.launch(arrayOf("image/*")) },
                contentScale = ContentScale.Crop,
            )
            Text(settings.profileName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("${library.playCounts.values.sum()} recorded plays · local profile", color = CathodeMuted)
        }
        HorizontalDivider(color = CathodeDim.copy(alpha = .4f))
        NavigationDrawerItem(
            label = { Text("Edit profile") },
            selected = false,
            icon = { Icon(Icons.Default.Edit, null) },
            onClick = { editing = true },
        )
        NavigationDrawerItem(
            label = { Column { Text("Transmission Log"); Text("Your listening record", color = CathodeMuted, style = MaterialTheme.typography.labelMedium) } },
            selected = false,
            icon = { Icon(Icons.Default.Equalizer, null, tint = CathodeCyan) },
            badge = { if (transmissionIsNew) Badge { Text("NEW") } },
            onClick = {
                store.update { it.copy(transmissionLogSeenYear = currentYear) }
                onClose()
                onTransmission()
            },
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            icon = { Icon(Icons.Default.Settings, null) },
            onClick = { onClose(); onSettings() },
        )
        Spacer(Modifier.weight(1f))
        Text("CATHODE // 12PTS", color = CathodeDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(20.dp))
    }
    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(32) }, label = { Text("Display name") }, singleLine = true)
                Button(onClick = { picker.launch(arrayOf("image/*")) }) { Text("Choose profile picture") }
            }
        },
        confirmButton = { TextButton(onClick = {
            store.update { it.copy(profileName = name.trim().ifEmpty { "Listener" }) }
            editing = false
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
    )
}
