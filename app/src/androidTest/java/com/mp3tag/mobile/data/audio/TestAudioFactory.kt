package com.mp3tag.mobile.data.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** Generates small deterministic audio files for the instrumentation tests. */
object TestAudioFactory {

    /** 1 s, 44.1 kHz, 16-bit stereo sine. */
    fun writeWav(file: File) {
        val rate = 44100
        val frames = rate
        val dataBytes = frames * 4
        FileOutputStream(file).use { out ->
            fun i16(v: Int) {
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
            }

            fun i32(v: Int) {
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
                out.write((v shr 16) and 0xFF)
                out.write((v shr 24) and 0xFF)
            }

            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            i32(36 + dataBytes)
            out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            i32(16)
            i16(1)
            i16(2)
            i32(rate)
            i32(rate * 4)
            i16(4)
            i16(16)
            out.write("data".toByteArray(Charsets.US_ASCII))
            i32(dataBytes)
            for (i in 0 until frames) {
                val t = i.toDouble() / rate
                i16((12000 * kotlin.math.sin(2 * Math.PI * 440 * t)).toInt())
                i16((9000 * kotlin.math.sin(2 * Math.PI * 660 * t)).toInt())
            }
        }
    }

    /** Minimal DSDIFF file: FRM8 / FVER / PROP(FS, CHNL) / DSD silence. */
    fun writeDff(file: File) {
        val rate = 2822400
        val channels = 2
        val bytesPerChannel = 8 * 4096
        val dsd = ByteArray(bytesPerChannel * channels) { 0x69 }

        val prop = ByteArrayOutputStream()
        prop.write("SND ".toByteArray(Charsets.US_ASCII))
        prop.write("FS  ".toByteArray(Charsets.US_ASCII))
        prop.write(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 4))
        prop.write(
            byteArrayOf(
                (rate ushr 24).toByte(),
                (rate ushr 16).toByte(),
                (rate ushr 8).toByte(),
                rate.toByte(),
            ),
        )
        prop.write("CHNL".toByteArray(Charsets.US_ASCII))
        prop.write(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 10))
        prop.write(byteArrayOf(0, channels.toByte()))
        prop.write("SLFTSRGT".toByteArray(Charsets.US_ASCII))
        val propBytes = prop.toByteArray()

        FileOutputStream(file).use { out ->
            fun u64(v: Long) {
                for (shift in 56 downTo 0 step 8) out.write(((v ushr shift) and 0xFF).toInt())
            }

            out.write("FRM8".toByteArray(Charsets.US_ASCII))
            u64((4 + 16 + 12 + propBytes.size + 12 + dsd.size).toLong())
            out.write("DSD ".toByteArray(Charsets.US_ASCII))
            out.write("FVER".toByteArray(Charsets.US_ASCII))
            u64(4)
            out.write(byteArrayOf(0, 1, 5, 0))
            out.write("PROP".toByteArray(Charsets.US_ASCII))
            u64(propBytes.size.toLong())
            out.write(propBytes)
            out.write("DSD ".toByteArray(Charsets.US_ASCII))
            u64(dsd.size.toLong())
            out.write(dsd)
        }
    }
}
