package com.twelvepts.cathode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twelvepts.cathode.LibraryState
import com.twelvepts.cathode.data.ListeningStat
import com.twelvepts.cathode.data.TransmissionYear
import com.twelvepts.cathode.model.AudioTrack
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun TransmissionLogScreen(library: LibraryState, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val availableYears = remember(library.transmissionYears) {
        (library.transmissionYears.keys + currentYear).distinct().sortedDescending()
    }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    val signal = library.transmissionYears[selectedYear] ?: TransmissionYear(selectedYear, emptyList(), 0, null)

    Box(Modifier.fillMaxSize().background(CathodeBlack)) {
        CathodeBackdrop(true)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CathodeBlack.copy(alpha = .28f), CathodeBlack.copy(alpha = .92f)))))
        AnimatedContent(
            targetState = selectedYear,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "transmission-year",
        ) {
            TransmissionYearContent(it, signal, library, availableYears, { value -> selectedYear = value }, onClose)
        }
    }
}

@Composable
private fun TransmissionYearContent(
    year: Int,
    signal: TransmissionYear,
    library: LibraryState,
    years: List<Int>,
    onYear: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val tracksByKey = remember(library.tracks) { library.tracks.associateBy(AudioTrack::stableKey) }
    val rankedTracks = signal.tracks.sortedWith(compareByDescending<ListeningStat> { it.listenedMs }.thenByDescending { it.playCount })
    val topTracks = rankedTracks.mapNotNull { stat -> tracksByKey[stat.trackKey]?.let { it to stat } }.take(10)
    val topArtists = rankedTracks.mapNotNull { stat -> tracksByKey[stat.trackKey]?.let { Triple(it.artist, stat.playCount, stat.listenedMs) } }
        .groupBy { it.first }
        .map { (artist, entries) -> RankedSignal(artist, entries.sumOf { it.second }, entries.sumOf { it.third }) }
        .sortedByDescending(RankedSignal::listenedMs).take(5)
    val topAlbums = rankedTracks.mapNotNull { stat -> tracksByKey[stat.trackKey]?.let { Triple(it.album, stat.playCount, stat.listenedMs) } }
        .groupBy { it.first }
        .map { (album, entries) -> RankedSignal(album, entries.sumOf { it.second }, entries.sumOf { it.third }) }
        .sortedByDescending(RankedSignal::listenedMs).take(5)
    val minutes = (signal.totalListenedMs / 60_000.0).roundToInt()
    val uniqueTracks = signal.tracks.count { it.playCount > 0 || it.listenedMs > 0 }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Close Transmission Log", tint = CathodeCyan) }
                Column(Modifier.weight(1f)) {
                    Text("TRANSMISSION LOG // $year", color = CathodeCyan, fontWeight = FontWeight.Bold)
                    Text("Your year in local music", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.forEach { value ->
                    FilterChip(selected = value == year, onClick = { onYear(value) }, label = { Text(value.toString()) })
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(CathodeCyan.copy(alpha = .22f), CathodePanel)))) {
                    Column(Modifier.padding(22.dp)) {
                        Text(signalTitle(signal), color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(formatMinutes(minutes), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("captured listening time", color = CathodeMuted)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalStat(signal.totalPlays.toString(), "PLAYS", Modifier.weight(1f))
                SignalStat(uniqueTracks.toString(), "TRACKS", Modifier.weight(1f))
                SignalStat(signal.activeDays.toString(), "ACTIVE DAYS", Modifier.weight(1f))
            }
        }
        if (signal.peakHour != null) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("PEAK RECEPTION", color = CathodeCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(hourLabel(signal.peakHour), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(daypartLabel(signal.peakHour), color = CathodeMuted)
                }
            }
        }
        if (topTracks.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("NO TRANSMISSIONS YET", color = CathodeCyan, fontWeight = FontWeight.Bold)
                        Text("A play is recorded when a track starts. Listening time begins after 30 seconds of active playback.", color = CathodeMuted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        } else {
            item { SignalHeading("TOP TRACKS") }
            itemsIndexed(topTracks, key = { _, item -> item.first.stableKey }) { index, (track, stat) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d".format(index + 1), color = CathodeCyan, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                    AsyncImage(track.artworkUri, null, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(CathodePanel), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = CathodeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${stat.playCount} plays", color = CathodeCyan, style = MaterialTheme.typography.labelMedium)
                        Text("${stat.listenedMs / 60_000} min", color = CathodeMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item { RankedSection("TOP ARTISTS", topArtists) }
            item { RankedSection("TOP ALBUMS", topAlbums) }
        }
        item {
            Text(
                "Cathode calculates this from playback on this device. It does not invent worldwide rankings or upload your history.",
                color = CathodeMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            )
        }
    }
}

private data class RankedSignal(val name: String, val plays: Int, val listenedMs: Long)

@Composable
private fun SignalStat(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = CathodeCyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, color = CathodeMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SignalHeading(label: String) {
    Text(label, color = CathodeCyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun RankedSection(title: String, entries: List<RankedSignal>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SignalHeading(title)
        val maximum = entries.maxOfOrNull(RankedSignal::listenedMs)?.coerceAtLeast(1) ?: 1
        entries.forEachIndexed { index, entry ->
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text("${index + 1}. ${entry.name}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.plays} plays", color = CathodeMuted, style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { entry.listenedMs.toFloat() / maximum.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = CathodeCyan,
                    trackColor = CathodeDim.copy(alpha = .3f),
                )
            }
        }
    }
}

private fun formatMinutes(minutes: Int): String =
    if (minutes < 60) "$minutes MIN" else "${minutes / 60} HR ${minutes % 60} MIN"

private fun signalTitle(signal: TransmissionYear): String = when {
    signal.totalListenedMs == 0L -> "AWAITING SIGNAL"
    signal.totalListenedMs >= 60L * 60L * 1000L -> "DEEP FREQUENCY"
    else -> "SIGNAL ACQUIRED"
}

private fun hourLabel(hour: Int): String {
    val normalized = hour % 24
    val display = when (val h = normalized % 12) { 0 -> 12; else -> h }
    return "$display:00 ${if (normalized < 12) "AM" else "PM"}"
}

private fun daypartLabel(hour: Int): String = when (hour) {
    in 5..11 -> "Morning listener"
    in 12..16 -> "Afternoon listener"
    in 17..21 -> "Evening listener"
    else -> "After-hours listener"
}
