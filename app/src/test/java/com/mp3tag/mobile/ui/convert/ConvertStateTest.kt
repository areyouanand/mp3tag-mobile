package com.mp3tag.mobile.ui.convert

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertStateTest {

    @Test
    fun `running and cancelling are the only active phases`() {
        assertFalse(ConvertViewModel.UiState().running)
        assertTrue(ConvertViewModel.UiState(phase = ConvertViewModel.ConvertPhase.RUNNING).running)
        assertTrue(ConvertViewModel.UiState(phase = ConvertViewModel.ConvertPhase.CANCELLING).running)
        assertFalse(ConvertViewModel.UiState(phase = ConvertViewModel.ConvertPhase.FINISHED).running)
    }

    @Test
    fun `cancel is only exposed while cancelling`() {
        assertFalse(ConvertViewModel.UiState(phase = ConvertViewModel.ConvertPhase.RUNNING).cancelRequested)
        assertTrue(ConvertViewModel.UiState(phase = ConvertViewModel.ConvertPhase.CANCELLING).cancelRequested)
    }
}
