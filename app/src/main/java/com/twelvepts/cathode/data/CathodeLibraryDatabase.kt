package com.twelvepts.cathode.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PlaylistSummary(val id: Long, val name: String, val trackCount: Int)

class CathodeLibraryDatabase(context: Context) :
    SQLiteOpenHelper(context, "cathode_library.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favorites (track_key TEXT PRIMARY KEY, added_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, track_key TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, track_key), FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE history (track_key TEXT PRIMARY KEY, play_count INTEGER NOT NULL, last_played INTEGER NOT NULL)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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

    fun deletePlaylist(id: Long) {
        writableDatabase.delete("playlists", "id=?", arrayOf(id.toString()))
    }

    fun playlists(): List<PlaylistSummary> = readableDatabase.rawQuery(
        "SELECT p.id, p.name, COUNT(t.track_key) FROM playlists p LEFT JOIN playlist_tracks t ON p.id=t.playlist_id GROUP BY p.id ORDER BY p.created_at DESC",
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(PlaylistSummary(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)))
        }
    }

    fun addToPlaylist(playlistId: Long, trackKey: String) {
        val next = readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id=?",
            arrayOf(playlistId.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        writableDatabase.insertWithOnConflict("playlist_tracks", null, ContentValues().apply {
            put("playlist_id", playlistId)
            put("track_key", trackKey)
            put("position", next)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeFromPlaylist(playlistId: Long, trackKey: String) {
        writableDatabase.delete("playlist_tracks", "playlist_id=? AND track_key=?", arrayOf(playlistId.toString(), trackKey))
    }

    fun playlistTrackKeys(playlistId: Long): List<String> = readableDatabase.rawQuery(
        "SELECT track_key FROM playlist_tracks WHERE playlist_id=? ORDER BY position",
        arrayOf(playlistId.toString()),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun recordPlay(trackKey: String) {
        writableDatabase.execSQL(
            "INSERT INTO history(track_key, play_count, last_played) VALUES(?,1,?) ON CONFLICT(track_key) DO UPDATE SET play_count=play_count+1,last_played=excluded.last_played",
            arrayOf(trackKey, System.currentTimeMillis()),
        )
    }

    fun recentTrackKeys(limit: Int = 50): List<String> = readableDatabase.rawQuery(
        "SELECT track_key FROM history ORDER BY last_played DESC LIMIT ?",
        arrayOf(limit.toString()),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun playCounts(): Map<String, Int> = readableDatabase.rawQuery(
        "SELECT track_key, play_count FROM history",
        null,
    ).use { cursor -> buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1)) } }
}
