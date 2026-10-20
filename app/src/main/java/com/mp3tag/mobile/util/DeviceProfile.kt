package com.mp3tag.mobile.util

import android.app.ActivityManager
import android.content.Context
import com.mp3tag.mobile.data.prefs.PerformanceMode

/**
 * Hardware classification used to scale concurrency, image decoding resolution,
 * and memory caching between low-spec budget phones and high-end DAPs/tablets/flagships.
 */
enum class HardwareTier(val label: String) {
    LOW_SPEC("Low-spec (memory & storage saver)"),
    STANDARD("Standard"),
    HIGH_END("High-end (fast parallel I/O & hi-res art)"),
}

data class DeviceCapabilities(
    val detectedTier: HardwareTier,
    val effectiveTier: HardwareTier,
    val cpuCores: Int,
    val totalRamMb: Int,
    val heapClassMb: Int,
    val isLowRamFlag: Boolean,
    /** Max concurrent files read/written at once on Dispatchers.IO. */
    val ioParallelism: Int,
    /** Target pixel size (longest side) for cover art thumbnails in the UI. */
    val thumbnailMaxPx: Int,
    /** Whether to decode UI thumbnails as RGB_565 (2 bytes/px) instead of ARGB_8888 (4 bytes/px). */
    val preferRgb565: Boolean,
    /** Max pixel dimension when importing a new cover image from the photo picker. */
    val maxImportedCoverPx: Int,
    /** Size of the in-memory decoded Bitmap LRU cache in KiB. */
    val bitmapCacheSizeKb: Int,
    /** Max total bytes of distinct raw cover art kept in RAM across a batch selection. */
    val maxRetainedCoverBytes: Long,
)

object DeviceProfile {

    @Volatile
    private var cached: DeviceCapabilities? = null

    @Volatile
    private var cachedMode: PerformanceMode? = null

    fun get(context: Context, mode: PerformanceMode = PerformanceMode.AUTO): DeviceCapabilities {
        val current = cached
        if (current != null && cachedMode == mode) return current

        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRamMb = if (memInfo.totalMem > 0) {
            (memInfo.totalMem / (1024L * 1024L)).toInt()
        } else {
            2048
        }
        val heapMb = am?.memoryClass ?: 128
        val isLowRam = am?.isLowRamDevice == true
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val detected = when {
            isLowRam || totalRamMb <= 3200 || heapMb <= 160 || cores <= 4 -> HardwareTier.LOW_SPEC
            totalRamMb >= 5800 && heapMb >= 256 && cores >= 6 -> HardwareTier.HIGH_END
            else -> HardwareTier.STANDARD
        }

        val effective = when (mode) {
            PerformanceMode.AUTO -> detected
            PerformanceMode.LOW_SPEC -> HardwareTier.LOW_SPEC
            PerformanceMode.HIGH_END -> HardwareTier.HIGH_END
        }

        val capabilities = when (effective) {
            HardwareTier.LOW_SPEC -> DeviceCapabilities(
                detectedTier = detected,
                effectiveTier = effective,
                cpuCores = cores,
                totalRamMb = totalRamMb,
                heapClassMb = heapMb,
                isLowRamFlag = isLowRam,
                ioParallelism = 2,
                thumbnailMaxPx = 256,
                preferRgb565 = true,
                maxImportedCoverPx = 1024,
                bitmapCacheSizeKb = (heapMb * 1024 / 16).coerceIn(2048, 8192),
                maxRetainedCoverBytes = 12L * 1024L * 1024L, // 12 MB cap on distinct covers
            )

            HardwareTier.STANDARD -> DeviceCapabilities(
                detectedTier = detected,
                effectiveTier = effective,
                cpuCores = cores,
                totalRamMb = totalRamMb,
                heapClassMb = heapMb,
                isLowRamFlag = isLowRam,
                ioParallelism = cores.coerceIn(2, 4),
                thumbnailMaxPx = 512,
                preferRgb565 = false,
                maxImportedCoverPx = 1600,
                bitmapCacheSizeKb = (heapMb * 1024 / 10).coerceIn(6144, 24576),
                maxRetainedCoverBytes = 32L * 1024L * 1024L, // 32 MB cap on distinct covers
            )

            HardwareTier.HIGH_END -> DeviceCapabilities(
                detectedTier = detected,
                effectiveTier = effective,
                cpuCores = cores,
                totalRamMb = totalRamMb,
                heapClassMb = heapMb,
                isLowRamFlag = isLowRam,
                ioParallelism = cores.coerceIn(4, 8),
                thumbnailMaxPx = 768,
                preferRgb565 = false,
                maxImportedCoverPx = 2400,
                bitmapCacheSizeKb = (heapMb * 1024 / 8).coerceIn(12288, 49152),
                maxRetainedCoverBytes = 64L * 1024L * 1024L, // 64 MB cap on distinct covers
            )
        }

        cached = capabilities
        cachedMode = mode
        return capabilities
    }
}
