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
import androidx.compose.ui.platform.LocalContext
import com.twelvepts.cathode.LibraryState
import com.twelvepts.cathode.data.CathodeDiagnostics
import com.twelvepts.cathode.data.SpotifyPlaylistResolver
import com.twelvepts.cathode.data.YouTubePlaylistResolver
import com.twelvepts.cathode.playback.PlaybackState
import com.twelvepts.cathode.playback.PlayerConnection
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: CathodeSettings,
    store: CathodeSettingsStore,
    artworkUri: Uri?,
    playback: PlaybackState,
    player: PlayerConnection,
    library: LibraryState,
    onAnalyzeLossless: () -> Unit,
    onReanalyzeLossless: () -> Unit,
    onCancelAnalysis: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spotify = remember { SpotifyPlaylistResolver(context.applicationContext) }
    val youtube = remember { YouTubePlaylistResolver(context.applicationContext) }
    var advanced by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var diagnosticEntries by remember { mutableStateOf(CathodeDiagnostics.entries(context)) }
    var spotifyClientId by remember { mutableStateOf(spotify.clientId) }
    var spotifyConnected by remember { mutableStateOf(spotify.isConnected) }
    var spotifyConnecting by remember { mutableStateOf(false) }
    var spotifyMessage by remember { mutableStateOf<String?>(null) }
    var youtubeApiKey by remember { mutableStateOf(youtube.apiKey) }
    var youtubeSaved by remember { mutableStateOf(youtube.isConfigured) }
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
                SettingSection("Playback")
                SettingToggle(
                    "Soft transition",
                    "Briefly fade at track boundaries.",
                    playback.transitionFadeEnabled,
                ) { player.setTransitionFade(it) }
                if (playback.transitionFadeEnabled) {
                    Text("${playback.transitionFadeSeconds} second fade", color = CathodeMuted)
                    Slider(
                        value = playback.transitionFadeSeconds.toFloat(),
                        onValueChange = { player.setTransitionFade(true, it.toInt()) },
                        valueRange = 1f..12f,
                        steps = 10,
                    )
                }
            }
            item {
                SettingSection("Spotify playlist import")
                Text(
                    "Cathode uses Spotify's official browser sign-in to read track and artist names. Spotify audio is never downloaded.",
                    color = CathodeMuted,
                )
                OutlinedTextField(
                    value = spotifyClientId,
                    onValueChange = { spotifyClientId = it.trim() },
                    label = { Text("Spotify Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(color = CathodePanel.copy(alpha = .8f), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("One-time setup", color = CathodeCyan, fontWeight = FontWeight.Bold)
                        Text("1. Create a Spotify developer app and select Web API.")
                        Text("2. Add this Redirect URI: http://127.0.0.1:43821/callback")
                        Text("3. Copy the Client ID generated by Spotify and paste it above.")
                        Text("Do not paste the Redirect URI or Client Secret into the Client ID field.", color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = spotifyClientId.isNotBlank() && !spotifyConnecting,
                        onClick = {
                            spotify.clientId = spotifyClientId
                            spotifyConnecting = true
                            spotifyMessage = "Waiting for Spotify authorization…"
                            scope.launch {
                                spotify.connect().fold(
                                    onSuccess = {
                                        spotifyConnected = true
                                        spotifyMessage = "Spotify connected."
                                    },
                                    onFailure = { spotifyMessage = it.message ?: "Spotify connection failed." },
                                )
                                spotifyConnecting = false
                            }
                        },
                    ) { Text(if (spotifyConnected) "Reconnect" else "Connect Spotify") }
                    if (spotifyConnected) TextButton(onClick = {
                        spotify.disconnect()
                        spotifyConnected = false
                        spotifyMessage = "Spotify disconnected."
                    }) { Text("Disconnect") }
                }
                spotifyMessage?.let { Text(it, color = if (spotifyConnected) CathodeCyan else CathodeMuted) }
            }
            item {
                SettingSection("YouTube playlist import")
                Text("Public YouTube and YouTube Music playlists use the official YouTube Data API. Private playlists require Google OAuth and are not supported yet.", color = CathodeMuted)
                OutlinedTextField(
                    value = youtubeApiKey,
                    onValueChange = { youtubeApiKey = it.trim(); youtubeSaved = false },
                    label = { Text("YouTube Data API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(color = CathodePanel.copy(alpha = .8f), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("One-time setup", color = CathodeCyan, fontWeight = FontWeight.Bold)
                        Text("1. Create a project in Google Cloud Console.")
                        Text("2. Enable YouTube Data API v3.")
                        Text("3. Create an API key and restrict it to YouTube Data API v3.")
                        Text("4. Paste that API key above. No Google password enters Cathode.")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = youtubeApiKey.isNotBlank(), onClick = {
                        youtube.apiKey = youtubeApiKey
                        youtubeSaved = true
                    }) { Text("Save API key") }
                    if (youtube.isConfigured || youtubeSaved) TextButton(onClick = {
                        youtube.clear()
                        youtubeApiKey = ""
                        youtubeSaved = false
                    }) { Text("Remove") }
                }
                if (youtubeSaved) Text("YouTube API configured.", color = CathodeCyan)
            }
            item {
                SettingSection("Audio integrity")
                Text(
                    "Spectral checks can flag a suspected lossy transcode, but cannot prove a file's original source. Results stay in diagnostics rather than labeling every track.",
                    color = CathodeMuted,
                )
                if (library.analysisRunning) {
                    Text("Analyzing ${library.analysisCompleted}/${library.analysisTotal}", color = CathodeCyan)
                    LinearProgressIndicator(
                        progress = { if (library.analysisTotal > 0) library.analysisCompleted.toFloat() / library.analysisTotal else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onCancelAnalysis) { Text("Cancel") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAnalyzeLossless) { Text("Analyze new files") }
                        OutlinedButton(onClick = onReanalyzeLossless) { Text("Reset & reanalyze") }
                    }
                    val analyzed = library.tracks.count { it.spectralAnalyzed }
                    val suspected = library.tracks.count { it.spectralAnalyzed && it.audioQuality.name == "SUSPECTED_TRANSCODE" }
                    Text("$analyzed analyzed · $suspected suspected transcodes", color = CathodeMuted)
                }
            }
            item {
                SettingSection("Diagnostics")
                Text("Runtime crashes, playback failures, download failures, and library scan errors are stored only on this device.", color = CathodeMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        diagnosticEntries = CathodeDiagnostics.entries(context)
                        showDiagnostics = true
                    }) { Text("View error log") }
                    TextButton(onClick = {
                        CathodeDiagnostics.clear(context)
                        diagnosticEntries = emptyList()
                    }) { Text("Clear") }
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
    if (showDiagnostics) AlertDialog(
        onDismissRequest = { showDiagnostics = false },
        title = { Text("Cathode diagnostics") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (diagnosticEntries.isEmpty()) item { Text("No recorded runtime errors.", color = CathodeMuted) }
                items(diagnosticEntries.size) { index -> Text(diagnosticEntries[index], style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text("Done") } },
    )
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
