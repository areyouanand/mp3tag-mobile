package com.mp3tag.mobile.data.convert

/** Output formats offered by the converter, mapped onto FFmpeg muxers/encoders. */
enum class ConvertTarget(
    val label: String,
    val extension: String,
    val muxer: String,
    val lossy: Boolean,
    val bitratesKbps: List<Int> = emptyList(),
    val description: String = "",
) {
    MP3(
        label = "MP3",
        extension = "mp3",
        muxer = "mp3",
        lossy = true,
        bitratesKbps = listOf(128, 192, 256, 320),
        description = "Most compatible · LAME encoder",
    ),
    FLAC(
        label = "FLAC",
        extension = "flac",
        muxer = "flac",
        lossy = false,
        description = "Lossless · smaller than WAV",
    ),
    M4A(
        label = "M4A (AAC)",
        extension = "m4a",
        muxer = "ipod",
        lossy = true,
        bitratesKbps = listOf(128, 192, 256, 320),
        description = "Apple / Android friendly · AAC",
    ),
    ALAC(
        label = "M4A (ALAC)",
        extension = "m4a",
        muxer = "ipod",
        lossy = false,
        description = "Apple lossless inside M4A",
    ),
    OPUS(
        label = "Opus",
        extension = "opus",
        muxer = "opus",
        lossy = true,
        bitratesKbps = listOf(96, 128, 160, 192, 256),
        description = "Best small-file quality · 48 kHz",
    ),
    OGG(
        label = "Ogg Vorbis",
        extension = "ogg",
        muxer = "ogg",
        lossy = true,
        bitratesKbps = listOf(128, 192, 256),
        description = "Legacy Ogg Vorbis",
    ),
    WAV(
        label = "WAV",
        extension = "wav",
        muxer = "wav",
        lossy = false,
        description = "Uncompressed PCM",
    ),
    AIFF(
        label = "AIFF",
        extension = "aiff",
        muxer = "aiff",
        lossy = false,
        description = "Uncompressed PCM (Apple)",
    ),
    WMA(
        label = "WMA",
        extension = "wma",
        muxer = "asf",
        lossy = true,
        bitratesKbps = listOf(128, 192, 256),
        description = "Windows Media Audio",
    ),
    WAVPACK(
        label = "WavPack",
        extension = "wv",
        muxer = "wv",
        lossy = false,
        description = "Lossless · hybrid-capable",
    ),
    ;

    /** FFmpeg encoder name; PCM bit depth only matters for WAV/AIFF. */
    fun encoderFor(bitDepth: Int): String = when (this) {
        MP3 -> "libmp3lame"
        FLAC -> "flac"
        M4A -> "aac"
        ALAC -> "alac"
        OPUS -> "libopus"
        OGG -> "vorbis"
        WMA -> "wmav2"
        WAVPACK -> "wavpack"
        WAV -> if (bitDepth >= 24) "pcm_s24le" else "pcm_s16le"
        AIFF -> if (bitDepth >= 24) "pcm_s24be" else "pcm_s16be"
    }

    /** FLAC / WavPack compression effort; 0 lets the encoder choose. */
    fun qualityFor(): Int = when (this) {
        FLAC -> 8
        WAVPACK -> 4
        else -> 0
    }

    val supportsBitDepth: Boolean get() = this == WAV || this == AIFF

    val defaultBitrate: Int get() = bitratesKbps.getOrNull(bitratesKbps.size - 1) ?: 0
}

/** User-visible conversion settings. Persisted defaults live in [com.mp3tag.mobile.data.prefs.AppPrefs]. */
data class ConvertSettings(
    val target: ConvertTarget = ConvertTarget.MP3,
    val bitrateKbps: Int = ConvertTarget.MP3.defaultBitrate,
    /** 0 = keep the source sample rate. */
    val sampleRateHz: Int = 0,
    /** 0 = keep the source channel count, 1 = mono, 2 = stereo. */
    val channels: Int = 0,
    /** 16 or 24; only used for WAV/AIFF. */
    val bitDepth: Int = 16,
    val keepTagsAndCover: Boolean = true,
    val deleteSourceAfterSuccess: Boolean = false,
) {
    fun normalized(): ConvertSettings {
        val allowed = target.bitratesKbps
        val bitrate = when {
            !target.lossy -> 0
            bitrateKbps in allowed -> bitrateKbps
            else -> target.defaultBitrate
        }
        val rate = when (sampleRateHz) {
            0, 22050, 32000, 44100, 48000, 88200, 96000, 176400, 192000 -> sampleRateHz
            else -> 0
        }
        return copy(bitrateKbps = bitrate, sampleRateHz = rate, channels = channels.coerceIn(0, 2))
    }
}

/** Sample-rate choices shown in the picker. */
val SAMPLE_RATE_CHOICES: List<Int> = listOf(0, 44100, 48000, 96000, 192000)

fun sampleRateLabel(hz: Int): String = when (hz) {
    0 -> "Keep source"
    44100 -> "44.1 kHz (CD)"
    48000 -> "48 kHz (video)"
    96000 -> "96 kHz (hi-res)"
    192000 -> "192 kHz (hi-res)"
    else -> "$hz Hz"
}

fun channelLabel(channels: Int): String = when (channels) {
    0 -> "Keep source"
    1 -> "Mono"
    else -> "Stereo"
}
