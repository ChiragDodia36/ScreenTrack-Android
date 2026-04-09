package com.screentrack.mobile

import kotlin.math.roundToInt

data class SummaryStats(
    val avgSentiment: Double,
    val dominantEmotion: String,
    val topContentType: String,
    val negativeRatio: Double
)

object SuggestionEngine {
    fun summarize(items: List<CaptureResultEntity>): SummaryStats {
        if (items.isEmpty()) {
            return SummaryStats(0.0, "unknown", "unknown", 0.0)
        }
        val avgSentiment = items.map { it.sentiment }.average()
        val dominantEmotion = items.groupingBy { it.emotion }.eachCount().maxByOrNull { it.value }?.key ?: "unknown"
        val topContentType = items.groupingBy { it.contentType }.eachCount().maxByOrNull { it.value }?.key ?: "unknown"
        val negativeRatio = items.count { it.sentiment < -0.2 } / items.size.toDouble()
        return SummaryStats(avgSentiment, dominantEmotion, topContentType, negativeRatio)
    }

    fun suggestionFor(stats: SummaryStats): String? {
        return when {
            stats.negativeRatio >= 0.6 || stats.avgSentiment <= -0.25 ->
                "You have seen a lot of negative content recently. Consider switching to something positive like music, comedy, or nature videos."
            stats.dominantEmotion.lowercase().contains("anger") || stats.dominantEmotion.lowercase().contains("fear") ->
                "A short break could help. Try a quick reset or calming content to balance your mood."
            stats.topContentType.lowercase().contains("news") || stats.topContentType.lowercase().contains("social") ->
                "Consider mixing in lighter content like music, learning, or a short walk."
            stats.avgSentiment >= 0.35 ->
                "Great job keeping content positive. Keep it up!"
            else -> null
        }
    }

    fun formatSummary(stats: SummaryStats): String {
        val sentiment = (stats.avgSentiment * 100).roundToInt()
        val negative = (stats.negativeRatio * 100).roundToInt()
        return "Avg sentiment: $sentiment, dominant emotion: ${stats.dominantEmotion}, top content: ${stats.topContentType}, negative: $negative%"
    }
}
