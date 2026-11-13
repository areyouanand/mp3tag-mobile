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

    /** Browser sort column + direction, persisted across launches. */
    var browserSortMode: String
        get() = prefs.getString(KEY_BROWSER_SORT, "NAME") ?: "NAME"
        set(value) = prefs.edit().putString(KEY_BROWSER_SORT, value).apply()

    var browserSortAscending: Boolean
        get() = prefs.getBoolean(KEY_BROWSER_SORT_ASC, true)
        set(value) = prefs.edit().putBoolean(KEY_BROWSER_SORT_ASC, value).apply()

    /** What Paste does when the destination name already exists. */
    var fileCollisionPolicy: String
        get() = prefs.getString(KEY_FILE_COLLISION, "KEEP_BOTH") ?: "KEEP_BOTH"
        set(value) = prefs.edit().putString(KEY_FILE_COLLISION, value).apply()

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

    // ---------------------------------------------------------------------
    // Settings backup / restore (JSON)
    // ---------------------------------------------------------------------

    fun exportSettingsJson(): String {
        val obj = org.json.JSONObject()
        obj.put("schema", 1)
        obj.put("id3Version", id3Version.name)
        obj.put("themeMode", themeMode.name)
        obj.put("designLanguage", designLanguage.name)
        obj.put("performanceMode", performanceMode.name)
        obj.put("updateChannel", updateChannel.name)
        obj.put("skipExistingReplayGain", skipExistingReplayGain)
        obj.put("writeOpusR128ByDefault", writeOpusR128ByDefault)
        obj.put("showHiddenFiles", showHiddenFiles)
        obj.put("browserSortMode", browserSortMode)
        obj.put("browserSortAscending", browserSortAscending)
        obj.put("fileCollisionPolicy", fileCollisionPolicy)
        obj.put("savedPatterns", org.json.JSONArray(savedPatterns))
        obj.put("recentFolders", org.json.JSONArray(recentFolders))
        obj.put("convertTarget", convertTarget)
        obj.put("convertBitrate", convertBitrate)
        obj.put("convertSampleRate", convertSampleRate)
        obj.put("convertChannels", convertChannels)
        obj.put("convertBitDepth", convertBitDepth)
        obj.put("convertKeepTags", convertKeepTags)
        obj.put("convertDeleteSource", convertDeleteSource)
        obj.put("convertOutputSubfolder", convertOutputSubfolder)
        obj.put("convertNamePattern", convertNamePattern)
        obj.put("convertPreserveTimestamps", convertPreserveTimestamps)
        obj.put("convertCompressionLevel", convertCompressionLevel)
        return obj.toString(2)
    }

    /** Returns true when the JSON looked like a settings backup. */
    fun importSettingsJson(text: String): Boolean = runCatching {
        val obj = org.json.JSONObject(text)
        if (!obj.has("schema")) return@runCatching false

        id3Version = Id3Version.entries.firstOrNull { it.name == obj.optString("id3Version") }
            ?: Id3Version.V2_3
        themeMode = ThemeMode.entries.firstOrNull { it.name == obj.optString("themeMode") }
            ?: ThemeMode.SYSTEM
        designLanguage = DesignLanguage.entries
            .firstOrNull { it.name == obj.optString("designLanguage") }
            ?: DesignLanguage.MATERIAL_3_EXPRESSIVE
        performanceMode = PerformanceMode.entries
            .firstOrNull { it.name == obj.optString("performanceMode") }
            ?: PerformanceMode.AUTO
        updateChannel = UpdateChannel.entries.firstOrNull { it.name == obj.optString("updateChannel") }
            ?: UpdateChannel.BETA
        skipExistingReplayGain = obj.optBoolean("skipExistingReplayGain", false)
        writeOpusR128ByDefault = obj.optBoolean("writeOpusR128ByDefault", true)
        showHiddenFiles = obj.optBoolean("showHiddenFiles", false)
        browserSortMode = obj.optString("browserSortMode", "NAME")
        browserSortAscending = obj.optBoolean("browserSortAscending", true)
        fileCollisionPolicy = obj.optString("fileCollisionPolicy", "KEEP_BOTH")
        obj.optJSONArray("savedPatterns")?.let { array ->
            savedPatterns = (0 until array.length()).mapNotNull { array.optString(it) }
        }
        obj.optJSONArray("recentFolders")?.let { array ->
            recentFolders = (0 until array.length()).mapNotNull { array.optString(it) }
        }
        obj.optString("convertTarget", "").takeIf { it.isNotBlank() }?.let { convertTarget = it }
        convertBitrate = obj.optInt("convertBitrate", convertBitrate)
        convertSampleRate = obj.optInt("convertSampleRate", convertSampleRate)
        convertChannels = obj.optInt("convertChannels", convertChannels)
        convertBitDepth = obj.optInt("convertBitDepth", convertBitDepth)
        convertKeepTags = obj.optBoolean("convertKeepTags", true)
        convertDeleteSource = obj.optBoolean("convertDeleteSource", false)
        convertOutputSubfolder = obj.optString("convertOutputSubfolder", "")
        convertNamePattern = obj.optString("convertNamePattern", "")
        convertPreserveTimestamps = obj.optBoolean("convertPreserveTimestamps", true)
        convertCompressionLevel = obj.optInt("convertCompressionLevel", 0)
        true
    }.getOrDefault(false)

    // ---------------------------------------------------------------------
    // Audio conversion defaults (Convert screen)
    // ---------------------------------------------------------------------

    var convertTarget: String?
        get() = prefs.getString(KEY_CONVERT_TARGET, null)
        set(value) = prefs.edit().putString(KEY_CONVERT_TARGET, value).apply()

    var convertBitrate: Int
        get() = prefs.getInt(KEY_CONVERT_BITRATE, 320)
        set(value) = prefs.edit().putInt(KEY_CONVERT_BITRATE, value).apply()

    var convertSampleRate: Int
        get() = prefs.getInt(KEY_CONVERT_SAMPLE_RATE, 0)
        set(value) = prefs.edit().putInt(KEY_CONVERT_SAMPLE_RATE, value).apply()

    var convertChannels: Int
        get() = prefs.getInt(KEY_CONVERT_CHANNELS, 0)
        set(value) = prefs.edit().putInt(KEY_CONVERT_CHANNELS, value).apply()

    var convertBitDepth: Int
        get() = prefs.getInt(KEY_CONVERT_BIT_DEPTH, 16)
        set(value) = prefs.edit().putInt(KEY_CONVERT_BIT_DEPTH, value).apply()

    var convertKeepTags: Boolean
        get() = prefs.getBoolean(KEY_CONVERT_KEEP_TAGS, true)
        set(value) = prefs.edit().putBoolean(KEY_CONVERT_KEEP_TAGS, value).apply()

    var convertDeleteSource: Boolean
        get() = prefs.getBoolean(KEY_CONVERT_DELETE_SOURCE, false)
        set(value) = prefs.edit().putBoolean(KEY_CONVERT_DELETE_SOURCE, value).apply()

    var convertOutputSubfolder: String
        get() = prefs.getString(KEY_CONVERT_SUBFOLDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CONVERT_SUBFOLDER, value).apply()

    var convertNamePattern: String
        get() = prefs.getString(KEY_CONVERT_PATTERN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CONVERT_PATTERN, value).apply()

    var convertPreserveTimestamps: Boolean
        get() = prefs.getBoolean(KEY_CONVERT_PRESERVE_TIME, true)
        set(value) = prefs.edit().putBoolean(KEY_CONVERT_PRESERVE_TIME, value).apply()

    var convertCompressionLevel: Int
        get() = prefs.getInt(KEY_CONVERT_COMPRESSION, 0)
        set(value) = prefs.edit().putInt(KEY_CONVERT_COMPRESSION, value).apply()

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
        private const val KEY_BROWSER_SORT = "browser_sort_mode"
        private const val KEY_BROWSER_SORT_ASC = "browser_sort_ascending"
        private const val KEY_FILE_COLLISION = "file_collision_policy"
        private const val KEY_SAVED_PATTERNS = "saved_patterns"
        private const val KEY_CONVERT_TARGET = "convert_target"
        private const val KEY_CONVERT_BITRATE = "convert_bitrate"
        private const val KEY_CONVERT_SAMPLE_RATE = "convert_sample_rate"
        private const val KEY_CONVERT_CHANNELS = "convert_channels"
        private const val KEY_CONVERT_BIT_DEPTH = "convert_bit_depth"
        private const val KEY_CONVERT_KEEP_TAGS = "convert_keep_tags"
        private const val KEY_CONVERT_DELETE_SOURCE = "convert_delete_source"
        private const val KEY_CONVERT_SUBFOLDER = "convert_output_subfolder"
        private const val KEY_CONVERT_PATTERN = "convert_name_pattern"
        private const val KEY_CONVERT_PRESERVE_TIME = "convert_preserve_timestamps"
        private const val KEY_CONVERT_COMPRESSION = "convert_compression_level"
        private const val RECENT_SEPARATOR = "\u0001"
        private const val MAX_RECENTS = 8
        private const val MAX_SAVED_PATTERNS = 12
    }
}
