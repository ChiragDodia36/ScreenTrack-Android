package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EkmanMapTest {
    @Test fun sevenLabels_sixBasicsPlusNeutral() {
        assertEquals(7, EkmanMap.EKMAN_EMOTIONS.size)
        listOf("anger", "disgust", "fear", "joy", "sadness", "surprise", "neutral")
            .forEach { assertTrue(it in EkmanMap.EKMAN_EMOTIONS) }
    }

    @Test fun collapsesGoEmotionsIntoBuckets() {
        val go = mapOf("amusement" to 0.5, "joy" to 0.3, "annoyance" to 0.2, "grief" to 0.4)
        val e = EkmanMap.toEkman(go)
        assertEquals(0.8, e["joy"]!!, 1e-9)      // amusement + joy
        assertEquals(0.2, e["anger"]!!, 1e-9)    // annoyance
        assertEquals(0.4, e["sadness"]!!, 1e-9)  // grief
        assertEquals(0.0, e["disgust"]!!, 1e-9)
    }

    @Test fun everyGoEmotionMapsSomewhere_totalPreserved() {
        val go = GO_EMOTIONS.associateWith { 1.0 }
        val e = EkmanMap.toEkman(go)
        assertEquals(28.0, e.values.sum(), 1e-9) // no emotion dropped
    }
}
