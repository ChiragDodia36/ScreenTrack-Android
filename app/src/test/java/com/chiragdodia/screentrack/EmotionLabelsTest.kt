package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionLabelsTest {
    @Test fun has28Labels() {
        assertEquals(28, GO_EMOTIONS.size)
    }

    @Test fun includesNeutralLast() {
        assertTrue("neutral" in GO_EMOTIONS)
        // model's id2label puts neutral at index 27 — order matters for logit mapping
        assertEquals("neutral", GO_EMOTIONS.last())
    }

    @Test fun noDuplicates() {
        assertEquals(GO_EMOTIONS.size, GO_EMOTIONS.toSet().size)
    }
}
