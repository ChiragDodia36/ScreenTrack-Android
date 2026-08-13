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

    @Test fun headerHasSevenEmotionsAndContentColumns() {
        val header = CsvExporter.buildHourlyCsv(emptyList()).lineSequence().first()
        assertTrue(header.startsWith("Date,Hour,"))
        EkmanMap.EKMAN_EMOTIONS.forEach { assertTrue("missing emotion col $it", header.contains(",$it")) }
        listOf("positive_pct", "negative_pct", "neutral_pct").forEach { assertTrue(header.contains(it)) }
        ContentClassifier.CATEGORY_NAMES.forEach { assertTrue("missing content col $it", header.contains(it)) }
    }

    @Test fun emptyInputIsHeaderOnly() {
        assertEquals(1, CsvExporter.buildHourlyCsv(emptyList()).trim().lines().size)
    }

    @Test fun oneHourAveragesScoresAndBucketsPercent() {
        val items = listOf(
            row(0L, mapOf("joy" to 0.8, "anger" to 0.0), sentiment = 0.7, content = "videos"),
            row(1000L, mapOf("joy" to 0.4, "anger" to 0.2), sentiment = -0.5, content = "humorous_content"),
        )
        val lines = CsvExporter.buildHourlyCsv(items).trim().lines()
        assertEquals(2, lines.size)
        val header = lines[0].split(",")
        val cells = lines[1].split(",")
        assertEquals(0.60, cells[header.indexOf("joy")].toDouble(), 0.001) // (0.8+0.4)/2
        assertEquals(50, cells[header.indexOf("positive_pct")].toInt())
        assertEquals(50, cells[header.indexOf("negative_pct")].toInt())
        assertEquals(50, cells[header.indexOf("videos")].toInt())
        assertEquals(50, cells[header.indexOf("humorous_content")].toInt())
    }
}
