package com.mp3tag.mobile.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

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

    /** Decodes [bytes] to a bitmap no larger than [maxSize] on its longest side. */
    fun decodeScaled(bytes: ByteArray, maxSize: Int = 512): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
