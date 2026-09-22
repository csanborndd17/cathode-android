package com.twelvepts.cathode.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.io.File

object CathodeBackup {
    private val preferenceNames = listOf("cathode_settings", "cathode_metadata", "playback_session")

    fun export(context: Context, database: CathodeLibraryDatabase): String = JSONObject().apply {
        put("schema", 1)
        put("product", "Cathode")
        put("database", database.exportData())
        put("generatedCovers", JSONObject().apply {
            File(context.filesDir, "covers").listFiles()?.filter(File::isFile)?.forEach { file ->
                put(file.name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        })
        put("preferences", JSONObject().apply {
            preferenceNames.forEach { name -> put(name, exportPreferences(context, name)) }
        })
    }.toString(2)

    fun restore(context: Context, database: CathodeLibraryDatabase, encoded: String) {
        val root = JSONObject(encoded)
        require(root.optString("product") == "Cathode") { "This is not a Cathode backup." }
        require(root.optInt("schema") == 1) { "Unsupported Cathode backup version." }
        database.restoreData(root.getJSONObject("database"))
        val coverDirectory = File(context.filesDir, "covers").apply { mkdirs() }
        root.optJSONObject("generatedCovers")?.let { covers ->
            covers.keys().forEach { name ->
                val safeName = File(name).name
                File(coverDirectory, safeName).writeBytes(Base64.decode(covers.getString(name), Base64.NO_WRAP))
            }
        }
        val preferences = root.getJSONObject("preferences")
        preferenceNames.forEach { name -> preferences.optJSONObject(name)?.let { restorePreferences(context, name, it) } }
    }

    private fun exportPreferences(context: Context, name: String): JSONObject = JSONObject().apply {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            when (value) {
                is Set<*> -> put(key, JSONObject().put("type", "set").put("value", JSONArray(value.filterIsInstance<String>())))
                is String -> put(key, JSONObject().put("type", "string").put("value", value))
                is Boolean -> put(key, JSONObject().put("type", "boolean").put("value", value))
                is Int -> put(key, JSONObject().put("type", "int").put("value", value))
                is Long -> put(key, JSONObject().put("type", "long").put("value", value))
                is Float -> put(key, JSONObject().put("type", "float").put("value", value.toDouble()))
            }
        }
    }

    private fun restorePreferences(context: Context, name: String, values: JSONObject) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        values.keys().forEach { key ->
            val entry = values.getJSONObject(key)
            when (entry.getString("type")) {
                "set" -> editor.putStringSet(key, buildSet {
                    val array = entry.getJSONArray("value")
                    for (index in 0 until array.length()) add(array.getString(index))
                })
                "string" -> editor.putString(key, entry.getString("value"))
                "boolean" -> editor.putBoolean(key, entry.getBoolean("value"))
                "int" -> editor.putInt(key, entry.getInt("value"))
                "long" -> editor.putLong(key, entry.getLong("value"))
                "float" -> editor.putFloat(key, entry.getDouble("value").toFloat())
            }
        }
        editor.apply()
    }
}
