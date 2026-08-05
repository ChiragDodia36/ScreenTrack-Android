package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceEmotionMapTest {
    @Test fun mapsEightDistinctGoEmotions() {
        assertEquals(8, FaceEmotionMap.FER_LABELS.size)
        assertEquals(8, FaceEmotionMap.MAPPED_GO.size) // all 8 map to distinct GoEmotions
        assertTrue("joy" in FaceEmotionMap.MAPPED_GO)
        assertTrue("disapproval" in FaceEmotionMap.MAPPED_GO) // contempt -> disapproval
    }

    @Test fun happinessBecomesJoyScore() {
        // FER_LABELS order: neutral, happiness, surprise, sadness, anger, disgust, fear, contempt
        val fer = listOf(0.05, 0.90, 0.01, 0.01, 0.01, 0.0, 0.01, 0.01)
        val go = FaceEmotionMap.toGoEmotions(fer)
        assertEquals(0.90, go["joy"]!!, 1e-9)
        assertEquals(0.05, go["neutral"]!!, 1e-9)
        assertTrue("joy" == go.maxByOrNull { it.value }!!.key)
    }

    @Test fun onlyMappedEmotionsPresent() {
        val go = FaceEmotionMap.toGoEmotions(List(8) { 0.125 })
        go.keys.forEach { assertTrue(it in FaceEmotionMap.MAPPED_GO) }
        assertTrue("admiration" !in go) // text-only emotion never comes from the face
    }
}
