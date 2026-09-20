package com.twelvepts.cathode.ui

import android.graphics.Color.parseColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(settings: CathodeSettings, store: CathodeSettingsStore) {
    var accentText by remember(settings.customAccentArgb) {
        mutableStateOf(settings.customAccentArgb?.let { "#%06X".format(0xFFFFFF and it) } ?: "")
    }
    var accentError by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (settings.compact) 8.dp else 14.dp),
    ) {
        item {
            Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Customize", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Make the player feel like yours.", color = CathodeMuted)
            }
        }
        item {
            SettingSection("Theme preset")
            ThemePreset.entries.forEach { preset ->
                FilterChip(
                    selected = settings.themePreset == preset && settings.customAccentArgb == null,
                    onClick = { store.update { it.copy(themePreset = preset, customAccentArgb = null) } },
                    label = { Text(preset.label) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        item {
            SettingSection("Custom accent")
            OutlinedTextField(
                value = accentText,
                onValueChange = {
                    accentText = it.take(7)
                    accentError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hex color") },
                placeholder = { Text("#00E5FF") },
                isError = accentError,
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching { parseColor(accentText) }
                        .onSuccess { color -> store.update { it.copy(customAccentArgb = color) } }
                        .onFailure { accentError = true }
                }) { Text("Apply") }
                Button(onClick = {
                    accentText = ""
                    store.update { it.copy(customAccentArgb = null) }
                }) { Text("Use preset") }
            }
        }
        item { SettingToggle("AMOLED black", "Use true black backgrounds.", settings.amoled) { value -> store.update { it.copy(amoled = value) } } }
        item { SettingToggle("Compact layout", "Fit more music and smaller controls onscreen.", settings.compact) { value -> store.update { it.copy(compact = value) } } }
        item { SettingToggle("Rounded surfaces", "Use softer artwork, panels, and controls.", settings.rounded) { value -> store.update { it.copy(rounded = value) } } }
        item { SettingToggle("Monospace typography", "Apply the technical Cathode typeface throughout.", settings.monospace) { value -> store.update { it.copy(monospace = value) } } }
        item { SettingToggle("Interface animations", "Enable transitions and artwork crossfades.", settings.animations) { value -> store.update { it.copy(animations = value) } } }
        item { SettingToggle("Startup reveal", "Show the Cathode logo when opening the app.", settings.startupAnimation) { value -> store.update { it.copy(startupAnimation = value) } } }
        item {
            SettingSection("Glow intensity")
            Slider(
                value = settings.glowStrength,
                onValueChange = { value -> store.update { it.copy(glowStrength = value) } },
                valueRange = 0f..1f,
            )
            Text("${(settings.glowStrength * 100).toInt()}%", color = CathodeMuted)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
