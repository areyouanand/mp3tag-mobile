package com.mp3tag.mobile.data.tag

import org.jaudiotagger.tag.TagField
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX

object Id3CustomFields {
    private fun frameBody(frame: AbstractID3v2Frame): FrameBodyTXXX? {
        return try {
            val bodyField = AbstractID3v2Frame::class.java.getDeclaredField("frameBody")
            bodyField.isAccessible = true
            bodyField.get(frame) as? FrameBodyTXXX
        } catch (_: Exception) {
            null
        }
    }

    fun setDescription(frame: AbstractID3v2Frame, description: String, text: String) {
        val body = frameBody(frame) ?: return
        body.description = description
        body.text = text
    }

    fun bodyDescription(frame: AbstractID3v2Frame): String? =
        frameBody(frame)?.description

    fun bodyText(frame: AbstractID3v2Frame): String? =
        frameBody(frame)?.text

    private fun allTxxxFrames(tag: AbstractID3v2Tag): List<AbstractID3v2Frame> {
        val raw = runCatching { tag.getFields("TXXX") }.getOrNull()
        if (raw != null) {
            return raw.filterIsInstance<AbstractID3v2Frame>()
        }
        return when (val frameObj = runCatching { tag.getFrame("TXXX") }.getOrNull()) {
            is AbstractID3v2Frame -> listOf(frameObj)
            is List<*> -> frameObj.filterIsInstance<AbstractID3v2Frame>()
            else -> emptyList()
        }
    }

    fun read(tag: AbstractID3v2Tag, keys: Set<String>): Map<String, String> {
        val upperWanted = keys.map { it.uppercase() }.toSet()
        val out = LinkedHashMap<String, String>()
        for (frame in allTxxxFrames(tag)) {
            val desc = bodyDescription(frame)?.trim() ?: continue
            val text = bodyText(frame)?.trim() ?: continue
            if (desc.uppercase() in upperWanted && text.isNotEmpty()) {
                out[desc.uppercase()] = text
            }
        }
        return out
    }

    fun write(tag: AbstractID3v2Tag, key: String, value: String?) {
        val existing = allTxxxFrames(tag)
        val kept = existing
            .filter { !bodyDescription(it).equals(key, ignoreCase = true) }
            .toMutableList<TagField>()
        if (!value.isNullOrBlank()) {
            val frame = tag.createFrame("TXXX") as AbstractID3v2Frame
            setDescription(frame, key, value)
            kept.add(frame)
        }
        if (kept.isEmpty()) {
            runCatching { tag.removeFrame("TXXX") }
        } else {
            tag.setFrame("TXXX", kept)
        }
    }
}
