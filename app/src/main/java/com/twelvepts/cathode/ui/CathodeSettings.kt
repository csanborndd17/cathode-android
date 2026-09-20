package com.twelvepts.cathode.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemePreset(val label: String) {
    CYAN("Cathode Cyan"), AMBER("Amber CRT"), GREEN("Phosphor Green"),
    ULTRAVIOLET("Ultraviolet"), ICE("Ice White"),
}
enum class LibraryCategory(val label: String) {
    SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), FOLDERS("Folders"),
}
enum class LibrarySort(val label: String) {
    RECENT("Recent"), TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DURATION("Duration"),
}
enum class NavigationStyle(val label: String) {
    LABELED("Icons + labels"), ICONS_ONLY("Icons only"), COMPACT("Compact"),
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
    val startupDestination: String = "Remember",
    val navigationStyle: NavigationStyle = NavigationStyle.LABELED,
    val tabOrder: List<String> = defaultTabs,
    val hiddenTabs: Set<String> = emptySet(),
    val homeSections: List<String> = defaultHomeSections,
    val hiddenHomeSections: Set<String> = emptySet(),
    val pinnedTrackKeys: Set<String> = emptySet(),
    val libraryCategory: LibraryCategory = LibraryCategory.SONGS,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    val libraryGrid: Boolean = false,
) {
    companion object {
        val defaultTabs = listOf("Home", "Search", "Library", "Acquire", "Settings")
        val defaultHomeSections = listOf("Pinned", "Recently added")
    }
}

class CathodeSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("cathode_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<CathodeSettings> = _state.asStateFlow()

    fun update(transform: (CathodeSettings) -> CathodeSettings) {
        val next = transform(_state.value)
        _state.value = next
        preferences.edit()
            .putString("theme", next.themePreset.name)
            .apply { if (next.customAccentArgb == null) remove("accent") else putInt("accent", next.customAccentArgb) }
            .putBoolean("amoled", next.amoled)
            .putBoolean("compact", next.compact)
            .putBoolean("rounded", next.rounded)
            .putBoolean("monospace", next.monospace)
            .putFloat("glow", next.glowStrength)
            .putBoolean("animations", next.animations)
            .putBoolean("startup", next.startupAnimation)
            .putString("last_tab", next.lastTab)
            .putString("startup_destination", next.startupDestination)
            .putString("navigation_style", next.navigationStyle.name)
            .putString("tab_order", next.tabOrder.joinToString(","))
            .putStringSet("hidden_tabs", next.hiddenTabs)
            .putString("home_sections", next.homeSections.joinToString(","))
            .putStringSet("hidden_home_sections", next.hiddenHomeSections)
            .putStringSet("pinned_track_keys", next.pinnedTrackKeys)
            .putString("library_category", next.libraryCategory.name)
            .putString("library_sort", next.librarySort.name)
            .putBoolean("library_grid", next.libraryGrid)
            .apply()
    }

    fun saveAppearanceProfile(settings: CathodeSettings) {
        val encoded = listOf(
            settings.themePreset.name,
            settings.customAccentArgb?.toString().orEmpty(),
            settings.amoled.toString(),
            settings.compact.toString(),
            settings.rounded.toString(),
            settings.monospace.toString(),
            settings.glowStrength.toString(),
            settings.animations.toString(),
            settings.startupAnimation.toString(),
        ).joinToString("|")
        preferences.edit().putString("saved_appearance_profile", encoded).apply()
    }

    fun applyAppearanceProfile() {
        val parts = preferences.getString("saved_appearance_profile", null)?.split("|") ?: return
        if (parts.size != 9) return
        update { current ->
            current.copy(
                themePreset = runCatching { ThemePreset.valueOf(parts[0]) }.getOrDefault(current.themePreset),
                customAccentArgb = parts[1].toIntOrNull(),
                amoled = parts[2].toBoolean(),
                compact = parts[3].toBoolean(),
                rounded = parts[4].toBoolean(),
                monospace = parts[5].toBoolean(),
                glowStrength = parts[6].toFloatOrNull()?.coerceIn(0f, 1f) ?: current.glowStrength,
                animations = parts[7].toBoolean(),
                startupAnimation = parts[8].toBoolean(),
            )
        }
    }

    private fun load(): CathodeSettings {
        val tabOrder = preferences.getString("tab_order", null)?.split(",")?.filter { it in CathodeSettings.defaultTabs }
            .orEmpty().let { saved -> saved + CathodeSettings.defaultTabs.filterNot(saved::contains) }
        val homeSections = preferences.getString("home_sections", null)?.split(",")
            ?.filter { it in CathodeSettings.defaultHomeSections }.orEmpty()
            .let { saved -> saved + CathodeSettings.defaultHomeSections.filterNot(saved::contains) }
        return CathodeSettings(
            themePreset = runCatching { ThemePreset.valueOf(preferences.getString("theme", ThemePreset.CYAN.name)!!) }.getOrDefault(ThemePreset.CYAN),
            customAccentArgb = if (preferences.contains("accent")) preferences.getInt("accent", 0) else null,
            amoled = preferences.getBoolean("amoled", false),
            compact = preferences.getBoolean("compact", false),
            rounded = preferences.getBoolean("rounded", true),
            monospace = preferences.getBoolean("monospace", false),
            glowStrength = preferences.getFloat("glow", .35f),
            animations = preferences.getBoolean("animations", true),
            startupAnimation = preferences.getBoolean("startup", true),
            lastTab = preferences.getString("last_tab", "Home") ?: "Home",
            startupDestination = preferences.getString("startup_destination", "Remember") ?: "Remember",
            navigationStyle = runCatching { NavigationStyle.valueOf(preferences.getString("navigation_style", NavigationStyle.LABELED.name)!!) }.getOrDefault(NavigationStyle.LABELED),
            tabOrder = tabOrder,
            hiddenTabs = preferences.getStringSet("hidden_tabs", emptySet()).orEmpty(),
            homeSections = homeSections,
            hiddenHomeSections = preferences.getStringSet("hidden_home_sections", emptySet()).orEmpty(),
            pinnedTrackKeys = preferences.getStringSet("pinned_track_keys", emptySet()).orEmpty(),
            libraryCategory = runCatching { LibraryCategory.valueOf(preferences.getString("library_category", LibraryCategory.SONGS.name)!!) }.getOrDefault(LibraryCategory.SONGS),
            librarySort = runCatching { LibrarySort.valueOf(preferences.getString("library_sort", LibrarySort.RECENT.name)!!) }.getOrDefault(LibrarySort.RECENT),
            libraryGrid = preferences.getBoolean("library_grid", false),
        )
    }
}
