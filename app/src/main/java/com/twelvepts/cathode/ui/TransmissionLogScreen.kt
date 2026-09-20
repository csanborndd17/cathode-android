package com.twelvepts.cathode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twelvepts.cathode.LibraryState

@Composable
fun TransmissionLogScreen(library: LibraryState, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val totalPlays = library.playCounts.values.sum()
    val topTracks = library.tracks.filter { (library.playCounts[it.stableKey] ?: 0) > 0 }
        .sortedByDescending { library.playCounts[it.stableKey] ?: 0 }.take(10)
    LazyColumn(
        Modifier.fillMaxSize().background(CathodeBlack).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Close Transmission Log") }
                Column {
                    Text("TRANSMISSION LOG", color = CathodeCyan, fontWeight = FontWeight.Bold)
                    Text("Your listening record", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    LogStat(totalPlays.toString(), "PLAYS")
                    LogStat(library.playCounts.count { it.value > 0 }.toString(), "TRACKS")
                    LogStat(library.favoriteKeys.size.toString(), "FAVORITES")
                }
            }
        }
        item { Text("Top transmissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (topTracks.isEmpty()) item {
            Text("Play music to begin building your local Transmission Log.", color = CathodeMuted)
        } else items(topTracks, key = { it.stableKey }) { track ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1)
                    Text(track.artist, color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
                }
                Text("${library.playCounts[track.stableKey] ?: 0} plays", color = CathodeCyan)
            }
        }
        item {
            Text("A richer yearly story—minutes, artists, albums, listening eras, and animated share cards—arrives with the dedicated listening-history release.", color = CathodeMuted, modifier = Modifier.padding(vertical = 20.dp))
        }
    }
}

@Composable
private fun LogStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = CathodeCyan, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, color = CathodeMuted, style = MaterialTheme.typography.labelSmall)
    }
}
