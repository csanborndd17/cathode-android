package com.twelvepts.cathode.ui

import android.graphics.Color.parseColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(settings: CathodeSettings, store: CathodeSettingsStore, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var advanced by remember { mutableStateOf(false) }
    var accentText by remember(settings.customAccentArgb) {
        mutableStateOf(settings.customAccentArgb?.let { "#%06X".format(0xFFFFFF and it) } ?: "")
    }
    var accentError by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(CathodeBlack)) {
        CathodeBackdrop(settings.animations)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CathodeBlack.copy(alpha = .38f), CathodeBlack.copy(alpha = .86f)))))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CATHODE", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onClose) { Text("Done") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !advanced, onClick = { advanced = false }, label = { Text("General") })
                FilterChip(selected = advanced, onClick = { advanced = true }, label = { Text("Advanced") })
            }
        }
        if (!advanced) {
            item {
                SettingSection("Appearance profile")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick={store.update { it.copy(themePreset=ThemePreset.CYAN,customAccentArgb=null,amoled=false,compact=false,rounded=true,monospace=false,glowStrength=.35f) }},label={Text("Cathode")})
                    AssistChip(onClick={store.update { it.copy(themePreset=ThemePreset.ICE,customAccentArgb=null,amoled=true,compact=true,rounded=true,monospace=false,glowStrength=.15f) }},label={Text("Midnight")})
                    AssistChip(onClick={store.update { it.copy(themePreset=ThemePreset.GREEN,customAccentArgb=null,amoled=true,compact=true,rounded=false,monospace=true,glowStrength=.55f) }},label={Text("Terminal")})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick={store.saveAppearanceProfile(settings)},label={Text("Save mine")})
                    AssistChip(onClick=store::applyAppearanceProfile,label={Text("Apply mine")})
                }
            }
            item {
                SettingSection("Theme")
                ThemePreset.entries.forEach { preset ->
                    FilterChip(selected=settings.themePreset==preset && settings.customAccentArgb==null,
                        onClick={store.update { it.copy(themePreset=preset,customAccentArgb=null) }},label={Text(preset.label)})
                }
            }
            item { SettingToggle("AMOLED black","Use true black backgrounds.",settings.amoled){v->store.update{it.copy(amoled=v)}} }
            item { SettingToggle("Compact layout","Fit more music onscreen.",settings.compact){v->store.update{it.copy(compact=v)}} }
            item { SettingToggle("Rounded surfaces","Use softer panels and controls.",settings.rounded){v->store.update{it.copy(rounded=v)}} }
            item { SettingToggle("Interface motion","Enable transitions and the phosphor background.",settings.animations){v->store.update{it.copy(animations=v)}} }
            item { SettingToggle("Startup reveal","Show the Cathode logo on launch.",settings.startupAnimation){v->store.update{it.copy(startupAnimation=v)}} }
        } else {
            item {
                SettingSection("Custom accent")
                OutlinedTextField(accentText,{accentText=it.take(7);accentError=false},Modifier.fillMaxWidth(),label={Text("Hex color")},placeholder={Text("#00E5FF")},isError=accentError,singleLine=true)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={runCatching{parseColor(accentText)}.onSuccess{color->store.update{it.copy(customAccentArgb=color)}}.onFailure{accentError=true}}){Text("Apply")}
                    TextButton(onClick={accentText="";store.update{it.copy(customAccentArgb=null)}}){Text("Use preset")}
                }
            }
            item { SettingToggle("Monospace typography","Use the technical Cathode typeface.",settings.monospace){v->store.update{it.copy(monospace=v)}} }
            item {
                SettingSection("Glow intensity")
                Slider(settings.glowStrength,{v->store.update{it.copy(glowStrength=v)}},valueRange=0f..1f)
                Text("${(settings.glowStrength*100).toInt()}%",color=CathodeMuted)
            }
            item {
                SettingSection("Startup destination")
                listOf("Remember","Listen","Library","Discover").forEach { destination ->
                    FilterChip(selected=settings.startupDestination==destination,onClick={store.update{it.copy(startupDestination=destination)}},label={Text(destination)})
                }
            }
            item {
                SettingSection("Navigation")
                NavigationStyle.entries.forEach { style ->
                    FilterChip(selected=settings.navigationStyle==style,onClick={store.update{it.copy(navigationStyle=style)}},label={Text(style.label)})
                }
            }
            item {
                SettingSection("Listen sections")
                settings.homeSections.forEach { name ->
                    SettingToggle(name,"Show this section on Listen.",name !in settings.hiddenHomeSections){visible->
                        store.update{it.copy(hiddenHomeSections=if(visible) it.hiddenHomeSections-name else it.hiddenHomeSections+name)}
                    }
                }
            }
            item {
                SettingSection("Data")
                Text("Metadata edits, favorites, playlists, history, and Transmission Log data stay on this device.",color=CathodeMuted)
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
        }
    }
}
@Composable private fun SettingSection(title:String){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
@Composable private fun SettingToggle(title:String,description:String,checked:Boolean,onChecked:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);Text(description,color=CathodeMuted,style=MaterialTheme.typography.labelMedium)}
        Switch(checked=checked,onCheckedChange=onChecked)
    }
}
