package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceSignalTest {
    @Test fun no_face_no_signal() {
        val s = FaceSignals.fromSmilingProbability(null)
        assertEquals(false, s.hadSignal)
        assertEquals(0.0, s.sentiment, 0.0001)
    }
    @Test fun big_smile_is_positive() {
        val s = FaceSignals.fromSmilingProbability(0.9f)
        assertTrue(s.hadSignal)
        assertTrue(s.sentiment > 0.0)
    }
    @Test fun no_smile_is_negative_leaning() {
        val s = FaceSignals.fromSmilingProbability(0.05f)
        assertTrue(s.hadSignal)
        assertTrue(s.sentiment < 0.0)
    }
}
