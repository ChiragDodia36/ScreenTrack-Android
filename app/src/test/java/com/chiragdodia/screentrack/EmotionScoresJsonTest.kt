package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionScoresJsonTest {
    @Test fun roundTrip() {
        val m = mapOf("joy" to 0.91, "anger" to 0.12, "neutral" to 0.03)
        val decoded = EmotionScoresJson.decode(EmotionScoresJson.encode(m))
        assertEquals(3, decoded.size)
        assertEquals(0.91, decoded["joy"]!!, 1e-9)
        assertEquals(0.12, decoded["anger"]!!, 1e-9)
    }

    @Test fun emptyStringDecodesEmpty() {
        assertTrue(EmotionScoresJson.decode("").isEmpty())
    }

    @Test fun emptyMapEncodesToEmptyObject() {
        assertEquals("{}", EmotionScoresJson.encode(emptyMap()))
    }
}
