package com.mp3tag.mobile.data.prefs

import android.content.Context
import android.content.SharedPreferences

enum class Id3Version(val label: String) {
    V2_3("ID3v2.3 (maximum compatibility)"),
    V2_4("ID3v2.4 (modern)"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Auto (follow system)"),
    DARK("Dark"),
    LIGHT("Light"),
}

enum class DesignLanguage(
    val label: String,
    val description: String,
) {
    MATERIAL_3_EXPRESSIVE(
        label = "Material 3 Expressive",
        description = "Expressive spring motion, bold pill geometry & dynamic tonal surfaces",
    ),
    MATERIAL_YOU(
        label = "Material You (Google Dynamic)",
        description = "Google's adaptive wallpaper-driven palette with classic Material 3 geometry",
    ),
    LIQUID_GLASS(
        label = "Liquid Glass",
        description = "Translucent frosted-glass surfaces, luminous cyan/azure highlights & fluid capsule shapes",
    ),
    HALO(
        label = "Halo",
        description = "Deep OLED-friendly contrast, glowing neon aura accents & crisp futuristic geometry",
    ),
}

enum class PerformanceMode(val label: String) {
    AUTO("Auto (match device hardware)"),
    HIGH_END("High performance (fast parallel I/O & hi-res covers)"),
    LOW_SPEC("Low-spec / memory saver (low RAM & slow SD safe)"),
}

enum class UpdateChannel(
    val label: String,
    val description: String,
) {
    RELEASE(
        label = "Release (Stable)",
        description = "Official stable builds verified on device",
    ),
    BETA(
        label = "Beta (Prerelease)",
        description = "Pre-release builds with the newest features & fixes",
    ),
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

    var designLanguage: DesignLanguage
        get() = DesignLanguage.entries.firstOrNull { it.name == prefs.getString(KEY_DESIGN_LANGUAGE, null) }
            ?: DesignLanguage.MATERIAL_3_EXPRESSIVE
        set(value) = prefs.edit().putString(KEY_DESIGN_LANGUAGE, value.name).apply()

    var performanceMode: PerformanceMode
        get() = PerformanceMode.entries.firstOrNull { it.name == prefs.getString(KEY_PERFORMANCE_MODE, null) }
            ?: PerformanceMode.AUTO
        set(value) = prefs.edit().putString(KEY_PERFORMANCE_MODE, value.name).apply()

    var updateChannel: UpdateChannel
        get() = UpdateChannel.entries.firstOrNull { it.name == prefs.getString(KEY_UPDATE_CHANNEL, null) }
            ?: UpdateChannel.BETA
        set(value) = prefs.edit().putString(KEY_UPDATE_CHANNEL, value.name).apply()

    var skipExistingReplayGain: Boolean
        get() = prefs.getBoolean(KEY_SKIP_EXISTING_RG, false)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_EXISTING_RG, value).apply()

    var writeOpusR128ByDefault: Boolean
        get() = prefs.getBoolean(KEY_WRITE_OPUS_R128, true)
        set(value) = prefs.edit().putBoolean(KEY_WRITE_OPUS_R128, value).apply()

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

    /** Folder the File Manager was in last. */
    var lastFileManagerFolder: String?
        get() = prefs.getString(KEY_LAST_FM_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_LAST_FM_FOLDER, value).apply()

    /** Whether hidden (dot) files and folders are shown in the File Manager. */
    var showHiddenFiles: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN_FILES, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN_FILES, value).apply()

    /** User-defined patterns saved from the tools dialogs, newest first. */
    var savedPatterns: List<String>
        get() = prefs.getString(KEY_SAVED_PATTERNS, null)
            ?.split(RECENT_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_SAVED_PATTERNS, value.take(MAX_SAVED_PATTERNS).joinToString(RECENT_SEPARATOR))
            .apply()

    fun clearRecentFolders() {
        prefs.edit().remove(KEY_RECENTS).apply()
    }

    fun clearSavedPatterns() {
        prefs.edit().remove(KEY_SAVED_PATTERNS).apply()
    }

    companion object {
        private const val KEY_ID3_VERSION = "id3_version"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DESIGN_LANGUAGE = "design_language"
        private const val KEY_PERFORMANCE_MODE = "performance_mode"
        private const val KEY_UPDATE_CHANNEL = "update_channel"
        private const val KEY_SKIP_EXISTING_RG = "skip_existing_rg"
        private const val KEY_WRITE_OPUS_R128 = "write_opus_r128"
        private const val KEY_RECENTS = "recent_folders"
        private const val KEY_LAST_FOLDER = "last_folder"
        private const val KEY_LAST_FM_FOLDER = "last_fm_folder"
        private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
        private const val KEY_SAVED_PATTERNS = "saved_patterns"
        private const val RECENT_SEPARATOR = "\u0001"
        private const val MAX_RECENTS = 8
        private const val MAX_SAVED_PATTERNS = 12
    }
}
