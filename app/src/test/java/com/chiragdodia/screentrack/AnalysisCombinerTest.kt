package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisCombinerTest {
    private val noContent = ContentResult("general", 1.0, hadSignal = false)

    private fun text(vararg s: Pair<String, Double>) = TextEmotion(s.toMap(), hadSignal = s.isNotEmpty())
    private fun face(vararg s: Pair<String, Double>) = FaceEmotion(s.toMap(), hadSignal = s.isNotEmpty())
    private val noText = TextEmotion(emptyMap(), hadSignal = false)
    private val noFace = FaceEmotion(emptyMap(), hadSignal = false)

    @Test fun neither_signal_is_undetermined() {
        val r = AnalysisCombiner.combine(noText, noFace, noContent)
        assertEquals("undetermined", r.emotion)
        assertEquals(0.0, r.sentiment, 1e-9)
        assertTrue(r.emotionScores.isEmpty())
    }

    @Test fun text_only_passes_scores_through() {
        val r = AnalysisCombiner.combine(text("joy" to 0.9, "neutral" to 0.1), noFace, noContent)
        assertEquals("joy", r.emotion)
        assertTrue(r.sentiment > 0.0)
        assertEquals(0.9, r.emotionScores["joy"]!!, 1e-9)
    }

    @Test fun face_only_uses_mapped_scores() {
        val r = AnalysisCombiner.combine(noText, face("joy" to 0.8), noContent)
        assertEquals("joy", r.emotion)
        assertTrue(r.sentiment > 0.0)
    }

    @Test fun both_blend_on_mapped_but_text_only_off_map() {
        val r = AnalysisCombiner.combine(
            text("joy" to 1.0, "admiration" to 0.5), // admiration is off the FER map
            face("joy" to 0.0),                       // face sees no joy
            ContentResult("entertainment", 0.5, hadSignal = true)
        )
        // joy is FER-mapped: 1.0*0.6 + 0.0*0.4 = 0.60
        assertEquals(0.60, r.emotionScores["joy"]!!, 1e-9)
        // admiration is off-map: stays text-only = 0.5
        assertEquals(0.50, r.emotionScores["admiration"]!!, 1e-9)
        assertEquals("entertainment", r.contentType)
    }
}
