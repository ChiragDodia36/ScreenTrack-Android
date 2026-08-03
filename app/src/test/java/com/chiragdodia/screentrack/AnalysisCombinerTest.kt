package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisCombinerTest {
    private val noContent = ContentResult("general", 1.0, hadSignal = false)

    /** Build a text signal from emotion scores. */
    private fun text(vararg scores: Pair<String, Double>): TextEmotion =
        TextEmotion(scores.toMap(), hadSignal = scores.isNotEmpty())

    private val noText = TextEmotion(emptyMap(), hadSignal = false)

    @Test fun neither_signal_is_undetermined() {
        val r = AnalysisCombiner.combine(noText, FaceSignal(0.0, hadSignal = false), noContent)
        assertEquals("undetermined", r.emotion)
        assertEquals(0.0, r.sentiment, 0.0001)
        assertEquals(0.0, r.emotionConfidence, 0.0001)
        assertTrue(r.emotionScores.isEmpty())
    }

    @Test fun text_only_uses_text_and_keeps_scores() {
        val r = AnalysisCombiner.combine(
            text("joy" to 0.9, "neutral" to 0.1),
            FaceSignal(0.0, hadSignal = false),
            noContent
        )
        assertEquals("joy", r.emotion)
        assertTrue(r.sentiment > 0.0)
        assertEquals(0.9, r.emotionScores["joy"]!!, 0.0001) // 28-score map passes through
    }

    @Test fun face_only_uses_face_sentiment() {
        val r = AnalysisCombiner.combine(noText, FaceSignal(0.7, hadSignal = true), noContent)
        assertTrue(r.sentiment > 0.0)
        assertEquals("joy", r.emotion) // positive face -> joy label
        assertTrue(r.emotionScores.isEmpty()) // no text model scores in Phase 1 face-only
    }

    @Test fun both_present_blends_sentiment() {
        val r = AnalysisCombiner.combine(
            text("sadness" to 0.8),
            FaceSignal(0.8, hadSignal = true),
            ContentResult("entertainment", 0.5, hadSignal = true)
        )
        // sadness sentiment (~-0.56) blended with +0.8 face lands between them
        assertTrue(r.sentiment > -0.56 && r.sentiment < 0.8)
        assertEquals("entertainment", r.contentType)
    }
}
