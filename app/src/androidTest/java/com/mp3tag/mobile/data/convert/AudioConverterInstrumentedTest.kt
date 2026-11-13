package com.mp3tag.mobile.data.convert

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mp3tag.mobile.data.audio.TestAudioFactory
import com.mp3tag.mobile.data.prefs.Id3Version
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end checks for the bundled FFmpeg transcoder. Run with:
 *   ./gradlew connectedDebugAndroidTest
 *
 * Every test converts a generated source file and verifies the container magic
 * bytes, so a broken JNI build fails loudly instead of silently producing files.
 */
@RunWith(AndroidJUnit4::class)
class AudioConverterInstrumentedTest {

    private lateinit var dir: File

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "convert-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun wavToMp3() = convert("tone.wav", ConvertTarget.MP3, "ID3", 0)

    @Test
    fun wavToFlac() = convert("tone.wav", ConvertTarget.FLAC, "fLaC", 0)

    @Test
    fun wavToM4a() = convert("tone.wav", ConvertTarget.M4A, "ftyp", 4)

    @Test
    fun wavToOpus() = convert("tone.wav", ConvertTarget.OPUS, "OggS", 0)

    @Test
    fun wavToWav24Bit() = convert("tone.wav", ConvertTarget.WAV, "RIFF", 0, bitDepth = 24)

    @Test
    fun dffToMp3UsesCustomDsdiffReader() {
        val source = File(dir, "silence.dff")
        TestAudioFactory.writeDff(source)
        val result = runConversion(source, ConvertTarget.MP3)
        assertTrue("conversion failed: ${result.error}", result.outputPath != null)
        assertMagic(File(result.outputPath!!), "ID3", 0)
    }

    @Test
    fun tagsSurviveConversion() {
        val source = taggedWav("tagged.wav")

        val result = runConversion(source, ConvertTarget.FLAC)
        assertTrue("conversion failed: ${result.error}", result.outputPath != null)
        val snapshot = com.mp3tag.mobile.data.tag.TagEngine.read(File(result.outputPath!!))
        assertEquals("warning=${result.warning}", "Test Tone", snapshot.fields[org.jaudiotagger.tag.FieldKey.TITLE])
        assertEquals("warning=${result.warning}", "Unit Test", snapshot.fields[org.jaudiotagger.tag.FieldKey.ARTIST])
        assertEquals("warning=${result.warning}", "Conversion Suite", snapshot.fields[org.jaudiotagger.tag.FieldKey.ALBUM])
        // FLAC artwork goes through FlacPictureHandler instead of JAudioTagger.
        assertTrue("FLAC cover art was not copied (warning=${result.warning})", snapshot.cover != null)
    }

    @Test
    fun customFieldsSurviveConversion() {
        val source = taggedWav("custom.wav")
        com.mp3tag.mobile.data.tag.TagEngine.write(
            source,
            com.mp3tag.mobile.data.tag.WriteRequest(
                customFields = mapOf(
                    "REPLAYGAIN_TRACK_GAIN" to "-7.25 dB",
                    "MY_CUSTOM_FIELD" to "hello",
                ),
            ),
            com.mp3tag.mobile.data.tag.WriteOptions(Id3Version.V2_3),
        )
        val sourceCustom = com.mp3tag.mobile.data.tag.TagEngine.readAllCustomFields(source)
        assertEquals("-7.25 dB", sourceCustom["REPLAYGAIN_TRACK_GAIN"])

        for (target in listOf(ConvertTarget.MP3, ConvertTarget.FLAC, ConvertTarget.OPUS)) {
            val result = runConversion(source, target)
            assertTrue("$target failed: ${result.error}", result.outputPath != null)
            val custom = com.mp3tag.mobile.data.tag.TagEngine.readAllCustomFields(File(result.outputPath!!))
            assertEquals(
                "$target (warning=${result.warning})",
                "-7.25 dB",
                custom["REPLAYGAIN_TRACK_GAIN"],
            )
            assertEquals("$target (warning=${result.warning})", "hello", custom["MY_CUSTOM_FIELD"])
        }
    }

    @Test
    fun coverSurvivesConversionToMp3() {
        val source = taggedWav("tagged-cover.wav")

        val result = runConversion(source, ConvertTarget.MP3)
        assertTrue("conversion failed: ${result.error}", result.outputPath != null)
        val outFile = File(result.outputPath!!)
        val snapshot = com.mp3tag.mobile.data.tag.TagEngine.read(outFile)
        assertEquals("warning=${result.warning}", "Test Tone", snapshot.fields[org.jaudiotagger.tag.FieldKey.TITLE])
        assertTrue("cover art was not copied (warning=${result.warning})", snapshot.cover != null)
    }

    private fun taggedWav(name: String): File {
        val source = File(dir, name)
        TestAudioFactory.writeWav(source)
        val cover = com.mp3tag.mobile.data.tag.CoverImage(onePixelPng(), "image/png")
        com.mp3tag.mobile.data.tag.TagEngine.write(
            source,
            com.mp3tag.mobile.data.tag.WriteRequest(
                fieldValues = mapOf(
                    org.jaudiotagger.tag.FieldKey.TITLE to "Test Tone",
                    org.jaudiotagger.tag.FieldKey.ARTIST to "Unit Test",
                    org.jaudiotagger.tag.FieldKey.ALBUM to "Conversion Suite",
                ),
                coverAction = com.mp3tag.mobile.data.tag.CoverAction.REPLACE,
                newCover = cover,
            ),
            com.mp3tag.mobile.data.tag.WriteOptions(Id3Version.V2_3),
        )
        val written = com.mp3tag.mobile.data.tag.TagEngine.read(source)
        assertEquals("source tags were not written", "Test Tone", written.fields[org.jaudiotagger.tag.FieldKey.TITLE])
        return source
    }

    private fun onePixelPng(): ByteArray = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        android.util.Base64.DEFAULT,
    )

    @Test
    fun multiValueArtistsSurviveConversion() {
        val source = taggedWav("multi.wav")
        com.mp3tag.mobile.data.tag.TagEngine.write(
            source,
            com.mp3tag.mobile.data.tag.WriteRequest(
                multiFields = mapOf(
                    org.jaudiotagger.tag.FieldKey.ARTIST to listOf("First Artist", "Second Artist"),
                ),
            ),
            com.mp3tag.mobile.data.tag.WriteOptions(Id3Version.V2_3),
        )
        assertEquals(
            listOf("First Artist", "Second Artist"),
            com.mp3tag.mobile.data.tag.TagEngine.read(source)
                .multiFields[org.jaudiotagger.tag.FieldKey.ARTIST],
        )

        for (target in listOf(ConvertTarget.FLAC, ConvertTarget.OPUS)) {
            val result = runConversion(source, target)
            assertTrue("$target failed: ${result.error}", result.outputPath != null)
            val snapshot = com.mp3tag.mobile.data.tag.TagEngine.read(File(result.outputPath!!))
            assertEquals(
                "$target (warning=${result.warning})",
                listOf("First Artist", "Second Artist"),
                snapshot.multiFields[org.jaudiotagger.tag.FieldKey.ARTIST],
            )
        }
    }

    @Test
    fun customFieldsSurviveM4aAndWma() {
        val source = taggedWav("mp4-custom.wav")
        com.mp3tag.mobile.data.tag.TagEngine.write(
            source,
            com.mp3tag.mobile.data.tag.WriteRequest(
                customFields = mapOf(
                    "REPLAYGAIN_TRACK_GAIN" to "-6.5 dB",
                    "MY_CUSTOM_FIELD" to "hello-mp4",
                ),
            ),
            com.mp3tag.mobile.data.tag.WriteOptions(Id3Version.V2_3),
        )
        for (target in listOf(ConvertTarget.M4A, ConvertTarget.WMA)) {
            val result = runConversion(source, target)
            assertTrue("$target failed: ${result.error}", result.outputPath != null)
            val custom = com.mp3tag.mobile.data.tag.TagEngine.readAllCustomFields(File(result.outputPath!!))
            assertEquals(
                "$target custom (warning=${result.warning})",
                "-6.5 dB",
                custom["REPLAYGAIN_TRACK_GAIN"],
            )
            assertEquals("$target custom (warning=${result.warning})", "hello-mp4", custom["MY_CUSTOM_FIELD"])
        }
    }

    @Test
    fun coversSurviveConversion() {
        val source = taggedWav("cover-matrix.wav")
        val supported = listOf(
            ConvertTarget.MP3,
            ConvertTarget.FLAC,
            ConvertTarget.M4A,
            ConvertTarget.ALAC,
            ConvertTarget.OPUS,
            ConvertTarget.OGG,
            ConvertTarget.WAV,
            ConvertTarget.AIFF,
            ConvertTarget.WMA,
            ConvertTarget.WAVPACK,
        )
        for (target in supported) {
            val result = runConversion(source, target)
            assertTrue("$target conversion failed: ${result.error}", result.outputPath != null)
            val snapshot = com.mp3tag.mobile.data.tag.TagEngine.read(File(result.outputPath!!))
            assertTrue(
                "$target lost the cover (warning=${result.warning})",
                snapshot.cover != null,
            )
        }
    }

    private fun convert(
        name: String,
        target: ConvertTarget,
        magic: String,
        offset: Int,
        bitDepth: Int = 16,
    ) {
        val source = File(dir, name)
        TestAudioFactory.writeWav(source)
        val result = runConversion(source, target, bitDepth)
        assertTrue("conversion failed: ${result.error}", result.outputPath != null)
        val out = File(result.outputPath!!)
        assertTrue("empty output", out.length() > 512)
        assertMagic(out, magic, offset)
    }

    private fun runConversion(
        source: File,
        target: ConvertTarget,
        bitDepth: Int = 16,
    ): ConversionFileResult {
        val outcome = runBlocking {
            AudioConverter.convert(
                paths = listOf(source.absolutePath),
                settings = ConvertSettings(target = target, bitDepth = bitDepth).normalized(),
                id3Version = Id3Version.V2_3,
                onProgress = {},
            )
        }
        assertEquals(1, outcome.results.size)
        return outcome.results.single()
    }

    private fun assertMagic(file: File, expected: String, offset: Int) {
        val bytes = file.inputStream().use { stream ->
            val buffer = ByteArray(offset + expected.length)
            stream.read(buffer)
            buffer
        }
        val actual = String(bytes, offset, expected.length, Charsets.US_ASCII)
        assertEquals("container magic of ${file.name}", expected, actual)
    }
}
