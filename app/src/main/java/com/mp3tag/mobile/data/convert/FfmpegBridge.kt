package com.mp3tag.mobile.data.convert

import android.util.Log

/**
 * Thin JNI binding around the statically linked LGPL FFmpeg build
 * (`native/ffmpeg_jni.c` + `libmp3tagffmpeg.so`).
 *
 * The native call is blocking, reports 0..100 % through [ProgressCallback] and can
 * be aborted from another thread through [cancel]. Only one conversion may run at
 * a time; the cancel flag is process-global.
 */
object FfmpegBridge {

    const val RESULT_OK = 0
    const val RESULT_ERROR = -1
    const val RESULT_CANCELLED = -2

    private const val TAG = "FfmpegBridge"

    private val loadFailure: String? = try {
        System.loadLibrary("mp3tagffmpeg")
        null
    } catch (t: Throwable) {
        Log.e(TAG, "Native FFmpeg library could not be loaded", t)
        t.message ?: t.javaClass.simpleName
    }

    val isAvailable: Boolean get() = loadFailure == null

    val unavailableReason: String? get() = loadFailure

    fun interface ProgressCallback {
        fun onProgress(percent: Int)
    }

    /** Version string from the linked libavutil, e.g. "8.0.3". */
    fun ffmpegVersion(): String = if (isAvailable) {
        runCatching { nativeVersion() }.getOrDefault("unknown")
    } else {
        "unavailable"
    }

    // -----------------------------------------------------------------
    // Streaming mono decoder (spectrum analysis / ReplayGain)
    // -----------------------------------------------------------------

    /** Opens a decoder and seeks to the middle window; 0 on failure (see [lastError]). */
    fun decodeOpen(path: String, windowSeconds: Double): Long =
        if (isAvailable) runCatching { nativeDecodeOpen(path, windowSeconds) }.getOrDefault(0L) else 0L

    fun decodeRead(handle: Long, maxSamples: Int): FloatArray? =
        if (isAvailable) runCatching { nativeDecodeRead(handle, maxSamples) }.getOrNull() else null

    /** `[sampleRate, channels, durationUs, orBits, orWidth, sourceBits]`. */
    fun decodeInfo(handle: Long): LongArray =
        if (isAvailable) runCatching { nativeDecodeInfo(handle) }.getOrDefault(LongArray(6))
        else LongArray(6)

    fun decodeCodec(handle: Long): String =
        if (isAvailable) runCatching { nativeDecodeCodec(handle) }.getOrDefault("") else ""

    fun decodeClose(handle: Long) {
        if (isAvailable && handle != 0L) runCatching { nativeDecodeClose(handle) }
    }

    /**
     * Converts [inputPath] into [outputPath].
     *
     * @param muxer FFmpeg muxer name (`mp3`, `flac`, `ipod`, `ogg`, `opus`, `wav`, `aiff`, `asf`, `wv`)
     * @param encoder FFmpeg encoder name (`libmp3lame`, `flac`, `aac`, `alac`, `libopus`, `vorbis`, `pcm_s24le`, `wmav2`, `wavpack`)
     * @param bitrateKbps target bitrate for lossy encoders, 0 for lossless
     * @param sampleRateHz target rate, 0 = keep the source rate
     * @param channels target channel count, 0 = keep the source channels
     * @param quality encoder effort (FLAC/WavPack compression level 0-12, 0 = encoder default)
     */
    fun transcode(
        inputPath: String,
        outputPath: String,
        muxer: String,
        encoder: String,
        bitrateKbps: Int,
        sampleRateHz: Int,
        channels: Int,
        quality: Int,
        callback: ProgressCallback?,
    ): Int {
        if (!isAvailable) return RESULT_ERROR
        return nativeTranscode(
            inputPath,
            outputPath,
            muxer,
            encoder,
            bitrateKbps,
            sampleRateHz,
            channels,
            quality,
            callback,
        )
    }

    /** Requests cancellation of the running conversion (checked between packets). */
    fun cancel() {
        if (isAvailable) nativeCancel()
    }

    fun lastError(): String =
        if (isAvailable) runCatching { nativeLastError() }.getOrDefault("Unknown error")
        else "Native FFmpeg library is unavailable: ${loadFailure ?: "unknown reason"}"

    @JvmStatic
    private external fun nativeTranscode(
        inputPath: String,
        outputPath: String,
        muxer: String,
        encoder: String,
        bitrateKbps: Int,
        sampleRateHz: Int,
        channels: Int,
        quality: Int,
        callback: ProgressCallback?,
    ): Int

    @JvmStatic
    private external fun nativeCancel()

    @JvmStatic
    private external fun nativeLastError(): String

    @JvmStatic
    private external fun nativeVersion(): String

    @JvmStatic
    private external fun nativeDecodeOpen(path: String, windowSeconds: Double): Long

    @JvmStatic
    private external fun nativeDecodeRead(handle: Long, maxSamples: Int): FloatArray?

    @JvmStatic
    private external fun nativeDecodeInfo(handle: Long): LongArray

    @JvmStatic
    private external fun nativeDecodeCodec(handle: Long): String

    @JvmStatic
    private external fun nativeDecodeClose(handle: Long)
}
