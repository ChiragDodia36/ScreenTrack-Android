package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseStateTest {
    @Test fun default_is_running() = assertEquals(false, PauseState().isPaused)
    @Test fun any_reason_pauses() {
        assertEquals(true, PauseState(screenOff = true).isPaused)
        assertEquals(true, PauseState(onCall = true).isPaused)
        assertEquals(true, PauseState(manual = true).isPaused)
    }
    @Test fun resumes_only_when_all_clear() {
        val s = PauseState(screenOff = true, onCall = true, manual = true)
        assertEquals(true, s.copy(screenOff = false, onCall = false).isPaused) // manual still on
        assertEquals(false, s.copy(screenOff = false, onCall = false, manual = false).isPaused)
    }
}
