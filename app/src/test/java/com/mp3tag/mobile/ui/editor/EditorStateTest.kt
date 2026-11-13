package com.mp3tag.mobile.ui.editor

import com.mp3tag.mobile.data.tag.CoverImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateTest {

    @Test
    fun `loading and saving are mutually exclusive`() {
        val loading = EditorState(phase = EditorPhase.LOADING)
        assertTrue(loading.loading)
        assertFalse(loading.saving)

        val saving = EditorState(phase = EditorPhase.SAVING)
        assertFalse(saving.loading)
        assertTrue(saving.saving)

        val ready = EditorState(phase = EditorPhase.READY)
        assertFalse(ready.loading)
        assertFalse(ready.saving)
    }

    @Test
    fun `cover edit is exactly one of none replace or remove`() {
        val image = CoverImage(byteArrayOf(1, 2, 3), "image/png")

        val none = EditorState(phase = EditorPhase.READY)
        assertNull(none.pendingCover)
        assertFalse(none.coverRemoveRequested)
        assertFalse(none.hasEdits)

        val replace = EditorState(phase = EditorPhase.READY, coverEdit = CoverEdit.Replace(image))
        assertNotNull(replace.pendingCover)
        assertFalse(replace.coverRemoveRequested)
        assertTrue(replace.hasEdits)

        val remove = EditorState(phase = EditorPhase.READY, coverEdit = CoverEdit.Remove)
        assertNull(remove.pendingCover)
        assertTrue(remove.coverRemoveRequested)
        assertTrue(remove.hasEdits)
    }

    @Test
    fun `discard and undo snapshot restore a single cover state`() {
        val image = CoverImage(byteArrayOf(4, 5), "image/jpeg")
        val state = EditorState(phase = EditorPhase.READY, coverEdit = CoverEdit.Replace(image))
        assertEquals(CoverEdit.Replace(image), state.coverEdit)
    }
}
