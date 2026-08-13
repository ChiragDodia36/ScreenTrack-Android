package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionSmootherTest {

    private fun cap(ts: Long, scores: EmotionScores) = CaptureResultEntity(
        timestampMs = ts, emotion = "x", emotionScore = 0.0, sentiment = 0.0,
        contentType = "general_or_miscellaneous", contentConfidence = 1.0,
        emotionScoresJson = EmotionScoresJson.encode(scores)
    )

    @Test fun emptyReturnsNull() {
        assertNull(EmotionSmoother.smooth(emptyList()))
    }

    @Test fun joySurvivesNeutralFlicker() {
        // Newest-first: 2 joy frames + 3 neutral frames (Ekman-keyed scores).
        val recent = listOf(
            cap(5000, mapOf("neutral" to 0.97, "joy" to 0.01)),
            cap(4000, mapOf("joy" to 0.70, "neutral" to 0.18)),
            cap(3000, mapOf("neutral" to 0.96, "joy" to 0.01)),
            cap(2000, mapOf("joy" to 0.72, "neutral" to 0.19)),
            cap(1000, mapOf("neutral" to 0.97)),
        )
        val out = EmotionSmoother.smooth(recent)!!
        // avg joy ≈ (0.01+0.70+0.01+0.72+0)/5 = 0.288 > 0.15 → wins over neutral
        assertEquals("joy", out.emotion)
        assertTrue(out.sentiment > 0.0)
    }

    @Test fun trulyNeutralStaysNeutral() {
        val recent = (1..4).map { cap(it * 1000L, mapOf("neutral" to 0.97, "joy" to 0.01)) }
        assertEquals("neutral", EmotionSmoother.smooth(recent)!!.emotion)
    }

    @Test fun allUndeterminedReturnsNewestUnchanged() {
        val recent = listOf(cap(2000, emptyMap()), cap(1000, emptyMap()))
        assertEquals(2000, EmotionSmoother.smooth(recent)!!.timestampMs)
    }
}
