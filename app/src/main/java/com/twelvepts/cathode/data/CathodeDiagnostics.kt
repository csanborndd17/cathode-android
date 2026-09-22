package com.twelvepts.cathode.data

import android.content.Context
import java.text.DateFormat
import java.util.Date

object CathodeDiagnostics {
    private const val PREFS = "cathode_diagnostics"
    private const val KEY = "events"
    private const val LIMIT = 80

    fun record(context: Context, category: String, message: String, throwable: Throwable? = null) {
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val detail = throwable?.let { " (${it.javaClass.simpleName}: ${it.message.orEmpty()})" }.orEmpty()
        val entry = "${DateFormat.getDateTimeInstance().format(Date())} · $category · $message$detail"
        preferences.edit().putStringSet(KEY, (entries(context) + entry).takeLast(LIMIT).toSet()).apply()
    }

    fun entries(context: Context): List<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()).orEmpty().sortedDescending()

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
