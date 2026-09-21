package com.twelvepts.cathode.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(settings: CathodeSettings, store: CathodeSettingsStore, artworkUri: Uri?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var advanced by remember { mutableStateOf(false) }
    val initialAccent = settings.customAccentArgb ?: 0xFF00E5FF.toInt()
    var accentRed by remember(settings.customAccentArgb) { mutableFloatStateOf(((initialAccent shr 16) and 0xff) / 255f) }
    var accentGreen by remember(settings.customAccentArgb) { mutableFloatStateOf(((initialAccent shr 8) and 0xff) / 255f) }
    var accentBlue by remember(settings.customAccentArgb) { mutableFloatStateOf((initialAccent and 0xff) / 255f) }
    Box(Modifier.fillMaxSize().background(CathodeBlack)) {
        ArtworkBackdrop(artworkUri, settings.animations)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CathodeBlack.copy(alpha = .22f), CathodeBlack.copy(alpha = .72f)))))
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                val preview = Color(accentRed, accentGreen, accentBlue, 1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.size(76.dp).background(preview, CircleShape))
                }
                ColorChannelSlider("Red", accentRed) { accentRed = it }
                ColorChannelSlider("Green", accentGreen) { accentGreen = it }
                ColorChannelSlider("Blue", accentBlue) { accentBlue = it }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={
                        val color = android.graphics.Color.rgb((accentRed*255).toInt(),(accentGreen*255).toInt(),(accentBlue*255).toInt())
                        store.update{it.copy(customAccentArgb=color)}
                    }){Text("Apply color")}
                    TextButton(onClick={store.update{it.copy(customAccentArgb=null)}}){Text("Use preset")}
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
                listOf("Remember","Home","Library","Discover").forEach { destination ->
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
                SettingSection("Home sections")
                settings.homeSections.forEach { name ->
                    SettingToggle(name,"Show this section on Home.",name !in settings.hiddenHomeSections){visible->
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
@Composable private fun ColorChannelSlider(label:String,value:Float,onValue:(Float)->Unit){
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = CathodeMuted)
            Text((value * 255).toInt().toString())
        }
        Slider(value,onValue,valueRange=0f..1f)
    }
}
@Composable private fun SettingToggle(title:String,description:String,checked:Boolean,onChecked:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);Text(description,color=CathodeMuted,style=MaterialTheme.typography.labelMedium)}
        Switch(checked=checked,onCheckedChange=onChecked)
    }
}
