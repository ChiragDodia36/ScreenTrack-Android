package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisCombinerTest {
    private val noContent = ContentResult("general_or_miscellaneous", 1.0, hadSignal = false)

    private fun text(vararg s: Pair<String, Double>) = TextEmotion(s.toMap(), hadSignal = s.isNotEmpty())
    private fun face(vararg s: Pair<String, Double>) = FaceEmotion(s.toMap(), hadSignal = s.isNotEmpty())
    private val noText = TextEmotion(emptyMap(), hadSignal = false)
    private val noFace = FaceEmotion(emptyMap(), hadSignal = false)

    @Test fun neither_signal_is_undetermined() {
        val r = AnalysisCombiner.combine(noText, noFace, noContent)
        assertEquals("undetermined", r.emotion)
        assertTrue(r.emotionScores.isEmpty())
    }

    @Test fun output_is_ekman_seven_only() {
        val r = AnalysisCombiner.combine(text("joy" to 0.9, "neutral" to 0.1), noFace, noContent)
        assertEquals("joy", r.emotion)
        assertEquals(EkmanMap.EKMAN_EMOTIONS.toSet(), r.emotionScores.keys)
        assertTrue(r.sentiment > 0.0)
    }

    @Test fun goEmotions_collapse_into_ekman_buckets() {
        // annoyance -> anger, admiration -> joy
        val r = AnalysisCombiner.combine(text("annoyance" to 0.8, "admiration" to 0.1), noFace, noContent)
        assertEquals("anger", r.emotion)
        assertEquals(0.8, r.emotionScores["anger"]!!, 1e-9)
        assertEquals(0.1, r.emotionScores["joy"]!!, 1e-9)
    }

    @Test fun face_only_maps_and_collapses() {
        val r = AnalysisCombiner.combine(noText, face("joy" to 0.8), noContent)
        assertEquals("joy", r.emotion)
        assertTrue(r.sentiment > 0.0)
    }

    @Test fun both_blend_then_collapse() {
        // joy blends (1.0*0.6 + 0.0*0.4 = 0.6); admiration off-map stays 0.5;
        // collapse joy bucket = 0.6 + 0.5 = 1.1
        val r = AnalysisCombiner.combine(
            text("joy" to 1.0, "admiration" to 0.5), face("joy" to 0.0),
            ContentResult("videos", 0.5, hadSignal = true)
        )
        assertEquals("joy", r.emotion)
        assertEquals(1.1, r.emotionScores["joy"]!!, 1e-9)
        assertEquals("videos", r.contentType)
    }
}
