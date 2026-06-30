package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentClassifierTest {
    @Test fun gaming_text_classified_gaming() {
        val r = ContentClassifier.classify("level 5 respawn score coins achievement among us imposter")
        assertEquals("gaming", r.contentType)
        assertTrue(r.hadSignal)
    }
    @Test fun education_text_classified_education() {
        val r = ContentClassifier.classify("lecture chapter homework tutorial study exam course")
        assertEquals("education", r.contentType)
    }
    @Test fun social_text_classified_social() {
        val r = ContentClassifier.classify("retweet like follow story reel comment")
        assertEquals("social_media", r.contentType)
    }
    @Test fun unclassifiable_is_general_without_signal() {
        val r = ContentClassifier.classify("xggh qqzz lorem 1234")
        assertEquals("general", r.contentType)
        assertEquals(false, r.hadSignal)
    }
}
