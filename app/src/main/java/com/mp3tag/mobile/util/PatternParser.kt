package com.mp3tag.mobile.util

import org.jaudiotagger.tag.FieldKey

/**
 * MP3Tag-style pattern handling for "%field%" templates, used both for
 * "tags from filename" parsing and "rename from tags" name building.
 */
object PatternParser {

    private val TOKENS: Map<String, FieldKey> = mapOf(
        "title" to FieldKey.TITLE,
        "artist" to FieldKey.ARTIST,
        "albumartist" to FieldKey.ALBUM_ARTIST,
        "album" to FieldKey.ALBUM,
        "year" to FieldKey.YEAR,
        "track" to FieldKey.TRACK,
        "tracktotal" to FieldKey.TRACK_TOTAL,
        "disc" to FieldKey.DISC_NO,
        "disctotal" to FieldKey.DISC_TOTAL,
        "genre" to FieldKey.GENRE,
        "composer" to FieldKey.COMPOSER,
        "comment" to FieldKey.COMMENT,
        "bpm" to FieldKey.BPM,
        "isrc" to FieldKey.ISRC,
    )

    /** Quick-pick patterns offered in the tools dialogs. */
    val COMMON_PATTERNS = listOf(
        "%artist% - %title%",
        "%title% - %artist%",
        "%track% - %title%",
        "%title%",
        "%artist% - %album% - %title%",
        "%year% - %artist% - %title%",
    )

    sealed interface Segment {
        data class Literal(val text: String) : Segment
        data class Field(val name: String, val key: FieldKey) : Segment
    }

    private val TOKEN_REGEX = Regex("%([A-Za-z]+)%")

    /** Splits a pattern into literal/field segments; null if it has no field tokens. */
    fun compile(pattern: String): List<Segment>? {
        if (pattern.isBlank()) return null
        val segments = mutableListOf<Segment>()
        var last = 0
        for (match in TOKEN_REGEX.findAll(pattern)) {
            if (match.range.first > last) {
                segments += Segment.Literal(pattern.substring(last, match.range.first))
            }
            val key = TOKENS[match.groupValues[1].lowercase()] ?: return null
            segments += Segment.Field(match.groupValues[1].lowercase(), key)
            last = match.range.last + 1
        }
        if (last < pattern.length) segments += Segment.Literal(pattern.substring(last))
        if (segments.isEmpty() || segments.none { it is Segment.Field }) return null
        return segments
    }

    /**
     * Matches a file name (without extension) against the compiled pattern.
     * Returns the extracted field values, or null if it does not match.
     * The last field token matches greedily so trailing separators stay in it.
     */
    fun match(segments: List<Segment>, fileNameNoExtension: String): Map<FieldKey, String>? {
        val regex = buildString {
            append("^")
            val fields = segments.count { it is Segment.Field }
            var index = 0
            for (segment in segments) {
                when (segment) {
                    is Segment.Literal -> append(Regex.escape(segment.text))
                    is Segment.Field -> {
                        index += 1
                        append(if (index == fields) "(.+)" else "(.+?)")
                    }
                }
            }
            append("$")
        }.toRegex()

        val result = regex.find(fileNameNoExtension.trim()) ?: return null
        val values = mutableMapOf<FieldKey, String>()
        var group = 1
        for (segment in segments) {
            if (segment is Segment.Field) {
                values[segment.key] = result.groupValues[group].trim()
                group += 1
            }
        }
        return values
    }

    /** Builds a file name (no extension) from tag values; null if any field is missing. */
    fun buildFileName(segments: List<Segment>, values: Map<FieldKey, String>): String? {
        val parts = segments.map { segment ->
            when (segment) {
                is Segment.Literal -> segment.text
                is Segment.Field -> values[segment.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            }
        }
        return parts.joinToString("").ifBlank { null }
    }

    /** Strips characters that are illegal in Windows/Android file names. */
    fun sanitizeFileName(name: String): String =
        name.replace(Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ' ')
            .ifBlank { "untitled" }

    /** Parses tags out of a file name using the given pattern. */
    fun parseFileName(pattern: String, fileName: String): Map<FieldKey, String>? {
        val segments = compile(pattern) ?: return null
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        return match(segments, base)
    }
}
