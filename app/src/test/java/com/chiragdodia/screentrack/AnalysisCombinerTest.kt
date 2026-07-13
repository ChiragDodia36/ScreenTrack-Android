package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisCombinerTest {
    private val noContent = ContentResult("general", 1.0, hadSignal = false)

    @Test fun neither_signal_is_undetermined() {
        val r = AnalysisCombiner.combine(
            TextEmotion("neutral", 1.0, 0.0, hadSignal = false),
            FaceSignal(0.0, hadSignal = false),
            noContent
        )
        assertEquals("undetermined", r.emotion)
        assertEquals(0.0, r.sentiment, 0.0001)
        assertEquals(0.0, r.emotionConfidence, 0.0001)
    }

    @Test fun text_only_uses_text() {
        val r = AnalysisCombiner.combine(
            TextEmotion("joy", 0.7, 0.8, hadSignal = true),
            FaceSignal(0.0, hadSignal = false),
            noContent
        )
        assertEquals("joy", r.emotion)
        assertTrue(r.sentiment > 0.0)
    }

    @Test fun face_only_uses_face_sentiment() {
        val r = AnalysisCombiner.combine(
            TextEmotion("neutral", 1.0, 0.0, hadSignal = false),
            FaceSignal(0.7, hadSignal = true),
            noContent
        )
        assertTrue(r.sentiment > 0.0)
        assertEquals("joy", r.emotion) // positive face -> joy label
    }

    @Test fun both_present_blends_sentiment() {
        val r = AnalysisCombiner.combine(
            TextEmotion("sadness", 0.6, -0.5, hadSignal = true),
            FaceSignal(0.8, hadSignal = true),
            ContentResult("entertainment", 0.5, hadSignal = true)
        )
        // blend of -0.5 and +0.8 lands between them
        assertTrue(r.sentiment > -0.5 && r.sentiment < 0.8)
        assertEquals("entertainment", r.contentType)
    }
}
