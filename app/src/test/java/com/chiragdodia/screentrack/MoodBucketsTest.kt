package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MoodBucketsTest {

    private fun cap(ts: Long, sentiment: Double, emotion: String) =
        CaptureResultEntity(timestampMs = ts, emotion = emotion, emotionScore = 1.0,
            sentiment = sentiment, contentType = "general", contentConfidence = 1.0, emotionScoresJson = "{}")

    private fun atHourToday(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test fun dayHasTwentyFourBuckets() {
        val out = MoodBuckets.build(emptyList(), MoodRange.DAY, System.currentTimeMillis(), 15.0)
        assertEquals(24, out.size)
        assertTrue("all empty when no data", out.all { it.isEmpty && it.netSentiment == 0.0 })
    }

    @Test fun weekAndMonthBucketCounts() {
        val now = System.currentTimeMillis()
        assertEquals(7, MoodBuckets.build(emptyList(), MoodRange.WEEK, now, 15.0).size)
        assertEquals(30, MoodBuckets.build(emptyList(), MoodRange.MONTH, now, 15.0).size)
    }

    @Test fun hourBucketAveragesSentimentAndFlagsNonEmpty() {
        val h = 10
        val items = listOf(
            cap(atHourToday(h, 5), sentiment = 0.6, emotion = "joy"),
            cap(atHourToday(h, 6), sentiment = -0.2, emotion = "joy"),
        )
        val out = MoodBuckets.build(items, MoodRange.DAY, System.currentTimeMillis(), 15.0)
        val bucket = out[h]
        assertFalse(bucket.isEmpty)
        assertEquals(0.20, bucket.netSentiment, 1e-9) // (0.6 + -0.2)/2
        assertEquals("joy", bucket.dominantEmotion)
        assertTrue("active minutes at least 1", bucket.activeMinutes >= 1)
        // other hours stay empty
        assertTrue(out[(h + 1) % 24].isEmpty)
    }

    @Test fun activeMinutesCapAtHour() {
        val h = 14
        val many = (0 until 1000).map { cap(atHourToday(h, 0) + it * 1000L, 0.1, "neutral") }
        val out = MoodBuckets.build(many, MoodRange.DAY, System.currentTimeMillis(), 60.0)
        assertTrue("never exceeds 60 min in an hour bucket", out[h].activeMinutes <= 60)
    }
}
