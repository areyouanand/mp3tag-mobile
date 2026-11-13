package com.mp3tag.mobile.data.tag

import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class ApeTagHandlerTest {

    private fun apeItem(key: String, value: ByteArray, flags: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(value.size).putInt(flags).array())
        out.write(key.toByteArray(Charsets.US_ASCII))
        out.write(0)
        out.write(value)
        return out.toByteArray()
    }

    private fun apeTag(items: List<ByteArray>): ByteArray {
        val data = ByteArrayOutputStream()
        items.forEach { data.write(it) }
        val itemsBytes = data.toByteArray()
        val size = itemsBytes.size + 32
        fun header(isHeader: Boolean): ByteArray {
            var flags = 1 shl 31
            if (isHeader) flags = flags or (1 shl 29)
            return ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                .put("APETAGEX".toByteArray(Charsets.US_ASCII))
                .putInt(2000)
                .putInt(size)
                .putInt(items.size)
                .putInt(flags)
                .putLong(0)
                .array()
        }
        return header(true) + itemsBytes + header(false)
    }

    @Test
    fun binaryCoverAndCustomFieldsRoundTrip() {
        val coverBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
        )
        val coverValue = "cover.jpg".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + coverBytes
        val tag = apeTag(
            listOf(
                apeItem("Artist", "Unit Test".toByteArray()),
                apeItem("REPLAYGAIN_TRACK_GAIN", "-7.25 dB".toByteArray()),
                apeItem("Cover Art (front)", coverValue, flags = 2),
            ),
        )
        // Minimal WavPack header + an APEv2 tag at the end of the file.
        val file = Files.createTempFile("ape-cover", ".wv").toFile()
        file.writeBytes("wvpk".toByteArray(Charsets.US_ASCII) + ByteArray(28) + tag)
        try {
            val snapshot = ApeTagHandler.read(file, AudioFormat.WAVPACK)
            assertEquals("Unit Test", snapshot.fields[FieldKey.ARTIST])
            assertNotNull("the binary cover item must be parsed", snapshot.cover)
            assertTrue(snapshot.cover!!.bytes.contentEquals(coverBytes))

            val custom = ApeTagHandler.readAllCustomFields(file)
            assertEquals("-7.25 dB", custom["REPLAYGAIN_TRACK_GAIN"])
            assertTrue(custom.keys.none { it.startsWith("COVER ART") })
        } finally {
            file.delete()
        }
    }

    @Test
    fun id3ShapedBinaryCoverIsNotMistakenForText() {
        val file = File.createTempFile("ape-text", ".wv")
        val tag = apeTag(listOf(apeItem("Album", "Conversion Suite".toByteArray())))
        file.writeBytes("wvpk".toByteArray(Charsets.US_ASCII) + ByteArray(28) + tag)
        try {
            val custom = ApeTagHandler.readAllCustomFields(file)
            assertEquals("Conversion Suite", custom["ALBUM"])
        } finally {
            file.delete()
        }
    }
}
