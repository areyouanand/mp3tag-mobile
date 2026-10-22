package com.mp3tag.mobile.data.tag

import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX

object Id3CustomFields {
    fun setDescription(frame: AbstractID3v2Frame, description: String, text: String) {
        val bodyField = AbstractID3v2Frame::class.java.getDeclaredField("frameBody")
        bodyField.isAccessible = true
        val body = bodyField.get(frame) as FrameBodyTXXX
        body.setDescription(description)
        body.setText(text)
    }

    fun bodyDescription(frame: AbstractID3v2Frame): String? {
        return try {
            val bodyField = AbstractID3v2Frame::class.java.getDeclaredField("frameBody")
            bodyField.isAccessible = true
            (bodyField.get(frame) as? FrameBodyTXXX)?.description
        } catch (e: Exception) {
            null
        }
    }

    fun write(tag: AbstractID3v2Tag, key: String, value: String?) {
        // Remove existing TXXX frames with the same description.
        val existing = tag.getFrame("TXXX").filterIsInstance<AbstractID3v2Frame>()
        val kept = existing.filter { bodyDescription(it) != key }.toMutableList()
        if (!value.isNullOrBlank()) {
            val frame = tag.createFrame("TXXX") as AbstractID3v2Frame
            setDescription(frame, key, value)
            kept.add(frame)
        }
        tag.setFrame("TXXX", kept as List<org.jaudiotagger.tag.TagField>)
    }
}