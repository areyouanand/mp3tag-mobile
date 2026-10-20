package com.mp3tag.mobile.data.prefs

import android.content.Context
import android.content.SharedPreferences

enum class Id3Version(val label: String) {
    V2_3("ID3v2.3 (maximum compatibility)"),
    V2_4("ID3v2.4 (modern)"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class PerformanceMode(val label: String) {
    AUTO("Auto (match device hardware)"),
    HIGH_END("High performance (fast parallel I/O & hi-res covers)"),
    LOW_SPEC("Low-spec / memory saver (low RAM & slow SD safe)"),
}

/**
 * Simple SharedPreferences-backed settings store.
 */
class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mp3tag_mobile", Context.MODE_PRIVATE)

    var id3Version: Id3Version
        get() = Id3Version.entries.firstOrNull { it.name == prefs.getString(KEY_ID3_VERSION, null) }
            ?: Id3Version.V2_3
        set(value) = prefs.edit().putString(KEY_ID3_VERSION, value.name).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) }
            ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var performanceMode: PerformanceMode
        get() = PerformanceMode.entries.firstOrNull { it.name == prefs.getString(KEY_PERFORMANCE_MODE, null) }
            ?: PerformanceMode.AUTO
        set(value) = prefs.edit().putString(KEY_PERFORMANCE_MODE, value.name).apply()

    /** Most-recently-visited folders, newest first. */
    var recentFolders: List<String>
        get() = prefs.getString(KEY_RECENTS, null)
            ?.split(RECENT_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_RECENTS, value.take(MAX_RECENTS).joinToString(RECENT_SEPARATOR))
            .apply()

    /** Folder the app was in last, reopened on start. */
    var lastFolder: String?
        get() = prefs.getString(KEY_LAST_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_LAST_FOLDER, value).apply()

    /** User-defined patterns saved from the tools dialogs, newest first. */
    var savedPatterns: List<String>
        get() = prefs.getString(KEY_SAVED_PATTERNS, null)
            ?.split(RECENT_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_SAVED_PATTERNS, value.take(MAX_SAVED_PATTERNS).joinToString(RECENT_SEPARATOR))
            .apply()

    companion object {
        private const val KEY_ID3_VERSION = "id3_version"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PERFORMANCE_MODE = "performance_mode"
        private const val KEY_RECENTS = "recent_folders"
        private const val KEY_LAST_FOLDER = "last_folder"
        private const val KEY_SAVED_PATTERNS = "saved_patterns"
        private const val RECENT_SEPARATOR = "\u0001"
        private const val MAX_RECENTS = 8
        private const val MAX_SAVED_PATTERNS = 12
    }
}
