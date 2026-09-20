package com.twelvepts.cathode.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemePreset(val label: String) {
    CYAN("Cathode Cyan"),
    AMBER("Amber CRT"),
    GREEN("Phosphor Green"),
    ULTRAVIOLET("Ultraviolet"),
    ICE("Ice White"),
}

data class CathodeSettings(
    val themePreset: ThemePreset = ThemePreset.CYAN,
    val customAccentArgb: Int? = null,
    val amoled: Boolean = false,
    val compact: Boolean = false,
    val rounded: Boolean = true,
    val monospace: Boolean = false,
    val glowStrength: Float = .35f,
    val animations: Boolean = true,
    val startupAnimation: Boolean = true,
    val lastTab: String = "Home",
)

class CathodeSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("cathode_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<CathodeSettings> = _state.asStateFlow()

    fun update(transform: (CathodeSettings) -> CathodeSettings) {
        val next = transform(_state.value)
        _state.value = next
        preferences.edit()
            .putString("theme", next.themePreset.name)
            .apply {
                if (next.customAccentArgb == null) remove("accent") else putInt("accent", next.customAccentArgb)
            }
            .putBoolean("amoled", next.amoled)
            .putBoolean("compact", next.compact)
            .putBoolean("rounded", next.rounded)
            .putBoolean("monospace", next.monospace)
            .putFloat("glow", next.glowStrength)
            .putBoolean("animations", next.animations)
            .putBoolean("startup", next.startupAnimation)
            .putString("last_tab", next.lastTab)
            .apply()
    }

    private fun load() = CathodeSettings(
        themePreset = runCatching {
            ThemePreset.valueOf(preferences.getString("theme", ThemePreset.CYAN.name)!!)
        }.getOrDefault(ThemePreset.CYAN),
        customAccentArgb = if (preferences.contains("accent")) preferences.getInt("accent", 0) else null,
        amoled = preferences.getBoolean("amoled", false),
        compact = preferences.getBoolean("compact", false),
        rounded = preferences.getBoolean("rounded", true),
        monospace = preferences.getBoolean("monospace", false),
        glowStrength = preferences.getFloat("glow", .35f),
        animations = preferences.getBoolean("animations", true),
        startupAnimation = preferences.getBoolean("startup", true),
        lastTab = preferences.getString("last_tab", "Home") ?: "Home",
    )
}
