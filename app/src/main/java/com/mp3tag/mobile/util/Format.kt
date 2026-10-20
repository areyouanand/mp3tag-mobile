package com.mp3tag.mobile.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream

object Format {

    fun fileSize(bytes: Long): String = when {
        bytes >= (1L shl 30) -> "%.1f GB".format(bytes / 1073741824.0)
        bytes >= (1L shl 20) -> "%.1f MB".format(bytes / 1048576.0)
        bytes >= (1L shl 10) -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    fun duration(seconds: Int?): String? {
        if (seconds == null || seconds <= 0) return null
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }
}

object ImageDecode {

    private val maxCacheKb: Int = run {
        val maxMemKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxMemKb / 12).coerceIn(2048, 32768)
    }

    private val bitmapCache = object : LruCache<Int, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    private fun cacheKey(contentHash: Int, maxSize: Int, preferRgb565: Boolean): Int {
        var result = contentHash
        result = 31 * result + maxSize
        result = 31 * result + if (preferRgb565) 1 else 0
        return result
    }

    fun getCached(contentHash: Int, maxSize: Int, preferRgb565: Boolean): Bitmap? {
        val key = cacheKey(contentHash, maxSize, preferRgb565)
        synchronized(bitmapCache) {
            return bitmapCache.get(key)?.takeUnless { it.isRecycled }
        }
    }

    fun clearCache() {
        synchronized(bitmapCache) {
            bitmapCache.evictAll()
        }
    }

    fun trimCache(level: Int) {
        synchronized(bitmapCache) {
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                bitmapCache.evictAll()
            } else {
                bitmapCache.trimToSize(bitmapCache.maxSize() / 2)
            }
        }
    }

    /**
     * Decodes [bytes] to a bitmap no larger than [maxSize] on its longest side.
     * Uses [Bitmap.Config.RGB_565] when [preferRgb565] is true (low-spec devices)
     * and falls back to coarser sampling if an [OutOfMemoryError] occurs.
     */
    fun decodeScaled(
        bytes: ByteArray,
        maxSize: Int = 512,
        preferRgb565: Boolean = false,
        contentHash: Int? = null,
    ): Bitmap? {
        val key = contentHash?.let { cacheKey(it, maxSize, preferRgb565) }
        if (key != null) {
            synchronized(bitmapCache) {
                bitmapCache.get(key)?.takeUnless { it.isRecycled }?.let { return it }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        } catch (_: OutOfMemoryError) {
            clearCache()
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2
        }

        val decoded = try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: OutOfMemoryError) {
            clearCache()
            try {
                val fallback = BitmapFactory.Options().apply {
                    inSampleSize = (sample * 2).coerceAtLeast(2)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, fallback)
            } catch (_: Throwable) {
                null
            }
        }

        if (decoded != null && key != null) {
            synchronized(bitmapCache) {
                bitmapCache.put(key, decoded)
            }
        }
        return decoded
    }

    /**
     * Downscales an imported cover image if its dimensions exceed [maxDimensionPx]
     * so embedding camera photos on low-RAM phones will not OOM JAudioTagger.
     */
    fun normalizeImportedCover(
        bytes: ByteArray,
        mimeType: String,
        maxDimensionPx: Int,
    ): Pair<ByteArray, String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || (w <= maxDimensionPx && h <= maxDimensionPx)) {
            return bytes to mimeType
        }
        val bitmap = decodeScaled(bytes, maxSize = maxDimensionPx, preferRgb565 = false)
            ?: return bytes to mimeType
        return try {
            val longest = maxOf(bitmap.width, bitmap.height)
            val scaled = if (longest > maxDimensionPx) {
                val ratio = maxDimensionPx.toFloat() / longest.toFloat()
                val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            if (scaled !== bitmap) scaled.recycle()
            out.toByteArray() to "image/jpeg"
        } catch (_: Throwable) {
            bytes to mimeType
        }
    }
}
