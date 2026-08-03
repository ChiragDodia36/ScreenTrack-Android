package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private fun row(ts: Long, scores: EmotionScores, sentiment: Double, content: String) =
        CaptureResultEntity(
            timestampMs = ts,
            emotion = scores.maxByOrNull { it.value }?.key ?: "neutral",
            emotionScore = scores.values.maxOrNull() ?: 0.0,
            sentiment = sentiment,
            contentType = content,
            contentConfidence = 1.0,
            emotionScoresJson = EmotionScoresJson.encode(scores)
        )

    @Test fun headerHasAllColumns() {
        val header = CsvExporter.buildHourlyCsv(emptyList()).lineSequence().first()
        assertTrue(header.startsWith("Date,Hour,"))
        GO_EMOTIONS.forEach { assertTrue("missing emotion col $it", header.contains(",$it")) }
        listOf("positive", "negative", "neutral").forEach { assertTrue(header.contains(it)) }
        ContentClassifier.CATEGORY_NAMES.forEach { assertTrue("missing content col $it", header.contains(it)) }
    }

    @Test fun emptyInputIsHeaderOnly() {
        val csv = CsvExporter.buildHourlyCsv(emptyList())
        assertEquals(1, csv.trim().lines().size)
    }

    @Test fun oneHourAveragesScoresAndBucketsPercent() {
        // Two captures in the same hour (ms 0 and 1000, same wall-clock hour).
        val items = listOf(
            row(0L, mapOf("joy" to 0.8, "anger" to 0.0), sentiment = 0.7, content = "news"),
            row(1000L, mapOf("joy" to 0.4, "anger" to 0.2), sentiment = -0.5, content = "gaming"),
        )
        val lines = CsvExporter.buildHourlyCsv(items).trim().lines()
        assertEquals(2, lines.size) // header + 1 hourly row
        val header = lines[0].split(",")
        val cells = lines[1].split(",")

        // joy average = (0.8 + 0.4)/2 = 0.60
        val joy = cells[header.indexOf("joy")].toDouble()
        assertEquals(0.60, joy, 0.001)
        // sentiment buckets: one >=0.2 (positive), one <=-0.2 (negative) -> 50/50, neutral 0
        assertEquals(50, cells[header.indexOf("positive")].toInt())
        assertEquals(50, cells[header.indexOf("negative")].toInt())
        // content: news 50, gaming 50
        assertEquals(50, cells[header.indexOf("news")].toInt())
        assertEquals(50, cells[header.indexOf("gaming")].toInt())
    }
}
