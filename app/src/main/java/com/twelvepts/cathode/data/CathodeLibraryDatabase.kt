package com.twelvepts.cathode.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PlaylistSummary(val id: Long, val name: String, val trackCount: Int, val artworkUri: String? = null)
data class ImportSessionTrack(val artist: String, val title: String, val status: String = "PENDING")
data class ImportSession(val inputText: String, val sourceId: String, val tracks: List<ImportSessionTrack>)
data class ListeningStat(val trackKey: String, val playCount: Int, val listenedMs: Long)
data class TransmissionYear(
    val year: Int,
    val tracks: List<ListeningStat>,
    val activeDays: Int,
    val peakHour: Int?,
) {
    val totalPlays: Int get() = tracks.sumOf(ListeningStat::playCount)
    val totalListenedMs: Long get() = tracks.sumOf(ListeningStat::listenedMs)
}

class CathodeLibraryDatabase(context: Context) :
    SQLiteOpenHelper(context, "cathode_library.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favorites (track_key TEXT PRIMARY KEY, added_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, artwork_uri TEXT, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, track_key TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, track_key), FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE history (track_key TEXT PRIMARY KEY, play_count INTEGER NOT NULL, last_played INTEGER NOT NULL)")
        createTransmissionTables(db)
        createImportTables(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createTransmissionTables(db)
        if (oldVersion < 3) createImportTables(db)
        if (oldVersion < 4) db.execSQL("ALTER TABLE playlists ADD COLUMN artwork_uri TEXT")
    }

    private fun createTransmissionTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS listening_stats (track_key TEXT NOT NULL, year INTEGER NOT NULL, play_count INTEGER NOT NULL DEFAULT 0, listened_ms INTEGER NOT NULL DEFAULT 0, last_played INTEGER NOT NULL, PRIMARY KEY(track_key, year))")
        db.execSQL("CREATE TABLE IF NOT EXISTS listening_events (id INTEGER PRIMARY KEY AUTOINCREMENT, track_key TEXT NOT NULL, year INTEGER NOT NULL, day_key TEXT NOT NULL, hour INTEGER NOT NULL, played_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS listening_events_year ON listening_events(year)")
    }

    private fun createImportTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS import_sessions (id INTEGER PRIMARY KEY, input_text TEXT NOT NULL, source_id TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS import_session_tracks (session_id INTEGER NOT NULL, position INTEGER NOT NULL, artist TEXT NOT NULL, title TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', PRIMARY KEY(session_id, position), FOREIGN KEY(session_id) REFERENCES import_sessions(id) ON DELETE CASCADE)")
    }

    fun saveImportSession(inputText: String, sourceId: String, tracks: List<ImportSessionTrack>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("import_session_tracks", "session_id=1", null)
            db.insertWithOnConflict("import_sessions", null, ContentValues().apply {
                put("id", 1); put("input_text", inputText); put("source_id", sourceId); put("updated_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE)
            tracks.forEachIndexed { position, track ->
                db.insert("import_session_tracks", null, ContentValues().apply {
                    put("session_id", 1); put("position", position); put("artist", track.artist); put("title", track.title); put("status", track.status)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun latestImportSession(): ImportSession? {
        val header = readableDatabase.rawQuery("SELECT input_text,source_id FROM import_sessions WHERE id=1", null).use {
            if (!it.moveToFirst()) return null
            it.getString(0) to it.getString(1)
        }
        val tracks = readableDatabase.rawQuery(
            "SELECT artist,title,status FROM import_session_tracks WHERE session_id=1 ORDER BY position", null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(ImportSessionTrack(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
        }
        return ImportSession(header.first, header.second, tracks)
    }

    fun favoriteKeys(): Set<String> = readableDatabase.rawQuery("SELECT track_key FROM favorites", null).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    fun toggleFavorite(trackKey: String): Boolean {
        val db = writableDatabase
        val exists = db.rawQuery("SELECT 1 FROM favorites WHERE track_key=?", arrayOf(trackKey)).use { it.moveToFirst() }
        if (exists) db.delete("favorites", "track_key=?", arrayOf(trackKey))
        else db.insert("favorites", null, ContentValues().apply {
            put("track_key", trackKey)
            put("added_at", System.currentTimeMillis())
        })
        return !exists
    }

    fun createPlaylist(name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return -1
        return writableDatabase.insert("playlists", null, ContentValues().apply {
            put("name", clean)
            put("created_at", System.currentTimeMillis())
        })
    }

    fun deletePlaylist(id: Long) { writableDatabase.delete("playlists", "id=?", arrayOf(id.toString())) }

    fun updatePlaylist(id: Long, name: String, artworkUri: String?) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        writableDatabase.update("playlists", ContentValues().apply {
            put("name", clean)
            if (artworkUri.isNullOrBlank()) putNull("artwork_uri") else put("artwork_uri", artworkUri)
        }, "id=?", arrayOf(id.toString()))
    }

    fun playlists(): List<PlaylistSummary> = readableDatabase.rawQuery(
        "SELECT p.id, p.name, COUNT(t.track_key), p.artwork_uri FROM playlists p LEFT JOIN playlist_tracks t ON p.id=t.playlist_id GROUP BY p.id ORDER BY p.created_at DESC", null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(PlaylistSummary(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3))) } }

    fun addToPlaylist(playlistId: Long, trackKey: String) {
        val next = readableDatabase.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id=?", arrayOf(playlistId.toString()))
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        writableDatabase.insertWithOnConflict("playlist_tracks", null, ContentValues().apply {
            put("playlist_id", playlistId); put("track_key", trackKey); put("position", next)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeFromPlaylist(playlistId: Long, trackKey: String) {
        writableDatabase.delete("playlist_tracks", "playlist_id=? AND track_key=?", arrayOf(playlistId.toString(), trackKey))
        normalizePlaylistPositions(playlistId)
    }

    fun movePlaylistTrack(playlistId: Long, trackKey: String, direction: Int) {
        val keys = playlistTrackKeys(playlistId).toMutableList()
        val from = keys.indexOf(trackKey)
        if (from < 0 || keys.isEmpty()) return
        val to = (from + direction).coerceIn(0, keys.lastIndex)
        if (from == to) return
        val moved = keys.removeAt(from)
        keys.add(to, moved)
        writePlaylistOrder(playlistId, keys)
    }

    private fun normalizePlaylistPositions(playlistId: Long) = writePlaylistOrder(playlistId, playlistTrackKeys(playlistId))

    private fun writePlaylistOrder(playlistId: Long, keys: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            keys.forEachIndexed { position, key ->
                db.update("playlist_tracks", ContentValues().apply { put("position", position) },
                    "playlist_id=? AND track_key=?", arrayOf(playlistId.toString(), key))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun playlistTrackKeys(playlistId: Long): List<String> = readableDatabase.rawQuery(
        "SELECT track_key FROM playlist_tracks WHERE playlist_id=? ORDER BY position", arrayOf(playlistId.toString()),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun recordPlaybackStart(trackKey: String) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        writableDatabase.beginTransaction()
        try {
            writableDatabase.execSQL(
                "INSERT INTO history(track_key, play_count, last_played) VALUES(?,1,?) ON CONFLICT(track_key) DO UPDATE SET play_count=play_count+1,last_played=excluded.last_played",
                arrayOf<Any>(trackKey, now),
            )
            writableDatabase.execSQL(
                "INSERT INTO listening_stats(track_key,year,play_count,listened_ms,last_played) VALUES(?,?,1,0,?) ON CONFLICT(track_key,year) DO UPDATE SET play_count=play_count+1,last_played=excluded.last_played",
                arrayOf<Any>(trackKey, year, now),
            )
            writableDatabase.insert("listening_events", null, ContentValues().apply {
                put("track_key", trackKey); put("year", year); put("day_key", day); put("hour", hour); put("played_at", now)
            })
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun recordListening(trackKey: String, listenedMs: Long) {
        if (listenedMs <= 0) return
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val now = System.currentTimeMillis()
        writableDatabase.execSQL(
            "INSERT INTO listening_stats(track_key,year,play_count,listened_ms,last_played) VALUES(?,?,0,?,?) ON CONFLICT(track_key,year) DO UPDATE SET listened_ms=listened_ms+excluded.listened_ms,last_played=excluded.last_played",
            arrayOf<Any>(trackKey, year, listenedMs.coerceAtMost(60_000), now),
        )
    }

    fun recentTrackKeys(limit: Int = 50): List<String> = readableDatabase.rawQuery(
        "SELECT track_key FROM history ORDER BY last_played DESC LIMIT ?", arrayOf(limit.toString()),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun playCounts(): Map<String, Int> = readableDatabase.rawQuery("SELECT track_key, play_count FROM history", null)
        .use { cursor -> buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1)) } }

    fun transmissionYears(): Map<Int, TransmissionYear> {
        val stats = readableDatabase.rawQuery(
            "SELECT track_key,year,play_count,listened_ms FROM listening_stats ORDER BY year DESC, listened_ms DESC", null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Triple(cursor.getInt(1), cursor.getString(0), Pair(cursor.getInt(2), cursor.getLong(3))))
            }
        }.groupBy { it.first }
        return stats.mapValues { (year, rows) ->
            val activeDays = readableDatabase.rawQuery("SELECT COUNT(DISTINCT day_key) FROM listening_events WHERE year=?", arrayOf(year.toString()))
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val peakHour = readableDatabase.rawQuery(
                "SELECT hour,COUNT(*) count FROM listening_events WHERE year=? GROUP BY hour ORDER BY count DESC LIMIT 1", arrayOf(year.toString()),
            ).use { if (it.moveToFirst()) it.getInt(0) else null }
            TransmissionYear(year, rows.map { ListeningStat(it.second, it.third.first, it.third.second) }, activeDays, peakHour)
        }
    }
}
