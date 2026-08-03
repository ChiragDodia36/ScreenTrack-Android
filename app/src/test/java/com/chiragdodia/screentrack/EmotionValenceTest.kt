package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionValenceTest {
    @Test fun everyLabelHasValence() {
        GO_EMOTIONS.forEach { assertTrue("missing valence for $it", it in EmotionValence.TABLE) }
    }

    @Test fun polarities() {
        assertTrue(EmotionValence.valence("joy") > 0)
        assertTrue(EmotionValence.valence("love") > 0)
        assertTrue(EmotionValence.valence("grief") < 0)
        assertTrue(EmotionValence.valence("anger") < 0)
        assertEquals(0.0, EmotionValence.valence("neutral"), 0.0)
    }

    @Test fun sentimentIsScoreWeighted() {
        // dominant joy -> strongly positive
        assertTrue(EmotionValence.sentimentOf(mapOf("joy" to 0.9, "neutral" to 0.1)) > 0.5)
        // dominant anger -> negative
        assertTrue(EmotionValence.sentimentOf(mapOf("anger" to 0.8)) < -0.4)
        // nothing -> 0
        assertEquals(0.0, EmotionValence.sentimentOf(emptyMap()), 0.0)
        assertEquals(0.0, EmotionValence.sentimentOf(mapOf("neutral" to 0.0)), 0.0)
    }
}
