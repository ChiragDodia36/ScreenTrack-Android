package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSentimentClassifierTest {
    @Test fun positive_text_is_joyful_and_positive() {
        val r = TextSentimentClassifier.classify("I am so happy and grateful, this is wonderful")
        assertEquals("joy", r.emotion)
        assertTrue("sentiment should be positive", r.sentiment > 0.0)
        assertTrue(r.hadSignal)
    }

    @Test fun negative_text_is_negative() {
        val r = TextSentimentClassifier.classify("this is terrible, I feel so sad and alone")
        assertTrue("sentiment should be negative", r.sentiment < 0.0)
        assertTrue(r.hadSignal)
    }

    @Test fun negation_flips_polarity() {
        val r = TextSentimentClassifier.classify("I am not happy at all")
        assertTrue("negated positive should not be positive", r.sentiment <= 0.0)
    }

    @Test fun no_emotion_words_has_no_signal() {
        val r = TextSentimentClassifier.classify("the train departs at 9 from platform two")
        assertEquals(false, r.hadSignal)
        assertEquals("neutral", r.emotion)
        assertEquals(0.0, r.sentiment, 0.0001)
    }

    @Test fun empty_text_has_no_signal() {
        val r = TextSentimentClassifier.classify("")
        assertEquals(false, r.hadSignal)
    }
}
