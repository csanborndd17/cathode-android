package com.twelvepts.cathode.ui

import android.graphics.Color.parseColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                Text("Appearance, Home, and navigation.", color = CathodeMuted)
            }
        }
        item {
            SettingSection("Appearance profiles")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { store.update { it.copy(themePreset=ThemePreset.CYAN, customAccentArgb=null, amoled=false, compact=false, rounded=true, monospace=false, glowStrength=.35f) } }, label={Text("Cathode")})
                AssistChip(onClick = { store.update { it.copy(themePreset=ThemePreset.ICE, customAccentArgb=null, amoled=true, compact=true, rounded=true, monospace=false, glowStrength=.15f) } }, label={Text("Midnight")})
                AssistChip(onClick = { store.update { it.copy(themePreset=ThemePreset.GREEN, customAccentArgb=null, amoled=true, compact=true, rounded=false, monospace=true, glowStrength=.55f) } }, label={Text("Terminal")})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { store.saveAppearanceProfile(settings) }, label = { Text("Save mine") })
                AssistChip(onClick = store::applyAppearanceProfile, label = { Text("Apply mine") })
            }
        }
        item {
            SettingSection("Theme preset")
            ThemePreset.entries.forEach { preset ->
                FilterChip(
                    selected = settings.themePreset == preset && settings.customAccentArgb == null,
                    onClick = { store.update { it.copy(themePreset = preset, customAccentArgb = null) } },
                    label = { Text(preset.label) }, modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        item {
            SettingSection("Custom accent")
            OutlinedTextField(accentText, { accentText=it.take(7); accentError=false }, Modifier.fillMaxWidth(),
                label={Text("Hex color")}, placeholder={Text("#00E5FF")}, isError=accentError, singleLine=true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick={
                    runCatching { parseColor(accentText) }
                        .onSuccess { color -> store.update { it.copy(customAccentArgb=color) } }
                        .onFailure { accentError=true }
                }) { Text("Apply") }
                Button(onClick={ accentText=""; store.update { it.copy(customAccentArgb=null) } }) { Text("Use preset") }
            }
        }
        item { SettingToggle("AMOLED black", "Use true black backgrounds.", settings.amoled) { v -> store.update { it.copy(amoled=v) } } }
        item { SettingToggle("Compact layout", "Fit more music onscreen.", settings.compact) { v -> store.update { it.copy(compact=v) } } }
        item { SettingToggle("Rounded surfaces", "Use softer panels and controls.", settings.rounded) { v -> store.update { it.copy(rounded=v) } } }
        item { SettingToggle("Monospace typography", "Use the technical Cathode typeface.", settings.monospace) { v -> store.update { it.copy(monospace=v) } } }
        item { SettingToggle("Interface animations", "Enable transitions and crossfades.", settings.animations) { v -> store.update { it.copy(animations=v) } } }
        item { SettingToggle("Startup reveal", "Show the Cathode logo on launch.", settings.startupAnimation) { v -> store.update { it.copy(startupAnimation=v) } } }
        item {
            SettingSection("Glow intensity")
            Slider(settings.glowStrength, { v -> store.update { it.copy(glowStrength=v) } }, valueRange=0f..1f)
            Text("${(settings.glowStrength*100).toInt()}%", color=CathodeMuted)
        }
        item {
            SettingSection("Startup destination")
            listOf("Remember","Home","Search","Library","Acquire").forEach { destination ->
                FilterChip(selected=settings.startupDestination==destination,
                    onClick={store.update { it.copy(startupDestination=destination) }}, label={Text(destination)},
                    modifier=Modifier.padding(end=6.dp))
            }
        }
        item {
            SettingSection("Navigation style")
            NavigationStyle.entries.forEach { style ->
                FilterChip(selected=settings.navigationStyle==style,
                    onClick={store.update { it.copy(navigationStyle=style) }}, label={Text(style.label)},
                    modifier=Modifier.padding(end=6.dp))
            }
        }
        item {
            SettingSection("Tabs")
            Text("Reorder tabs or hide optional destinations.", color=CathodeMuted, style=MaterialTheme.typography.labelMedium)
        }
        items(settings.tabOrder.size) { index ->
            val name=settings.tabOrder[index]
            OrderedRow(name, index, settings.tabOrder.lastIndex,
                enabled=name !in settings.hiddenTabs,
                canHide=name!="Home" && name!="Settings",
                onEnabled={ visible -> store.update { it.copy(hiddenTabs=if(visible) it.hiddenTabs-name else it.hiddenTabs+name) } },
                onMove={ delta -> store.update { current ->
                    val list=current.tabOrder.toMutableList()
                    val target=(index+delta).coerceIn(0,list.lastIndex)
                    val value=list.removeAt(index); list.add(target,value)
                    current.copy(tabOrder=list)
                } })
        }
        item {
            SettingSection("Home sections")
            Text("Choose their order and visibility.", color=CathodeMuted, style=MaterialTheme.typography.labelMedium)
        }
        items(settings.homeSections.size) { index ->
            val name=settings.homeSections[index]
            OrderedRow(
                name, index, settings.homeSections.lastIndex,
                enabled = name !in settings.hiddenHomeSections,
                canHide = true,
                onEnabled = { visible -> store.update {
                    it.copy(hiddenHomeSections = if (visible) it.hiddenHomeSections - name else it.hiddenHomeSections + name)
                } },
                onMove={ delta -> store.update { current ->
                    val list=current.homeSections.toMutableList()
                    val target=(index+delta).coerceIn(0,list.lastIndex)
                    val value=list.removeAt(index); list.add(target,value)
                    current.copy(homeSections=list)
                } },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable private fun OrderedRow(name:String,index:Int,lastIndex:Int,enabled:Boolean,canHide:Boolean,onEnabled:(Boolean)->Unit,onMove:(Int)->Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), fontWeight=FontWeight.SemiBold)
        TextButton(onClick={onMove(-1)}, enabled=index>0) { Text("↑") }
        TextButton(onClick={onMove(1)}, enabled=index<lastIndex) { Text("↓") }
        if(canHide) Switch(checked=enabled,onCheckedChange=onEnabled)
    }
}
@Composable private fun SettingSection(title:String) {
    Text(title, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
}
@Composable private fun SettingToggle(title:String,description:String,checked:Boolean,onChecked:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title,fontWeight=FontWeight.SemiBold)
            Text(description,color=CathodeMuted,style=MaterialTheme.typography.labelMedium)
        }
        Switch(checked=checked,onCheckedChange=onChecked)
    }
}
