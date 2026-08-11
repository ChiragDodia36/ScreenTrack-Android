package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionSmootherTest {

    private fun cap(ts: Long, scores: EmotionScores) = CaptureResultEntity(
        timestampMs = ts, emotion = "x", emotionScore = 0.0, sentiment = 0.0,
        contentType = "general", contentConfidence = 1.0,
        emotionScoresJson = EmotionScoresJson.encode(scores)
    )

    @Test fun emptyReturnsNull() {
        assertNull(EmotionSmoother.smooth(emptyList()))
    }

    @Test fun amusementSurvivesNeutralFlicker() {
        // Newest-first: 2 amusement frames + 3 neutral frames within the window.
        val recent = listOf(
            cap(5000, mapOf("neutral" to 0.97, "amusement" to 0.01)),
            cap(4000, mapOf("amusement" to 0.70, "joy" to 0.2, "neutral" to 0.18)),
            cap(3000, mapOf("neutral" to 0.96, "amusement" to 0.01)),
            cap(2000, mapOf("amusement" to 0.72, "neutral" to 0.19)),
            cap(1000, mapOf("neutral" to 0.97)),
        )
        val out = EmotionSmoother.smooth(recent)!!
        // avg amusement ≈ (0.01+0.70+0.01+0.72+0)/5 = 0.288 > 0.15 → wins over neutral
        assertEquals("amusement", out.emotion)
        assertTrue("mildly positive", out.sentiment > 0.0)
    }

    @Test fun trulyNeutralStaysNeutral() {
        val recent = (1..4).map { cap(it * 1000L, mapOf("neutral" to 0.97, "approval" to 0.01)) }
        assertEquals("neutral", EmotionSmoother.smooth(recent)!!.emotion)
    }

    @Test fun allUndeterminedReturnsNewestUnchanged() {
        val recent = listOf(cap(2000, emptyMap()), cap(1000, emptyMap()))
        assertEquals(2000, EmotionSmoother.smooth(recent)!!.timestampMs)
    }
}
