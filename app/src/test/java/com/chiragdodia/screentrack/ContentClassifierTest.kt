package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentClassifierTest {
    @Test fun detectsVideos() {
        val r = ContentClassifier.classify("YouTube · watch?v=abc · Subscribe · 2M views · shorts")
        assertEquals("videos", r.contentType)
        assertTrue(r.hadSignal)
    }

    @Test fun detectsHumorousContent() {
        val r = ContentClassifier.classify("funniest memes comedy hilarious try not to laugh prank")
        assertEquals("humorous_content", r.contentType)
    }

    @Test fun detectsFoodRecipesCooking() {
        val r = ContentClassifier.classify("easy recipe · cooking · ingredients · bake · delicious meal")
        assertEquals("food_recipes_cooking", r.contentType)
    }

    @Test fun gibberishFallsBackToGeneral() {
        val r = ContentClassifier.classify("xggh qqzz lorem 1234")
        assertEquals("general_or_miscellaneous", r.contentType)
        assertTrue(!r.hadSignal)
    }

    @Test fun categoryNamesHasSevenWithGeneralLast() {
        assertEquals(7, ContentClassifier.CATEGORY_NAMES.size)
        assertEquals("general_or_miscellaneous", ContentClassifier.CATEGORY_NAMES.last())
    }
}
