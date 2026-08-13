package com.chiragdodia.screentrack

/**
 * Rolling display emotion over the most recent captures. DISPLAY-ONLY — never
 * changes stored data, the chart, or the CSV. It averages the recent 28-score
 * maps and suppresses "neutral" so the live label doesn't flicker to neutral
 * between emotional frames (e.g. a funny video between captions). Pure — tested.
 */
object EmotionSmoother {

    // A non-neutral emotion this strong (averaged over the window) wins the label.
    private const val SUPPRESS_THRESHOLD = 0.15
    // Don't smooth across a long pause (e.g. app resumed after hours).
    private const val MAX_GAP_MS = 5 * 60 * 1000L

    /** [recentDesc] is newest-first. Returns a synthesized capture for display. */
    fun smooth(recentDesc: List<CaptureResultEntity>): CaptureResultEntity? {
        if (recentDesc.isEmpty()) return null
        val newest = recentDesc.first()
        val window = recentDesc.filter { newest.timestampMs - it.timestampMs <= MAX_GAP_MS }
        val signal = window.map { EmotionScoresJson.decode(it.emotionScoresJson) }.filter { it.isNotEmpty() }
        if (signal.isEmpty()) return newest // all undetermined — leave as-is

        val avg = EkmanMap.EKMAN_EMOTIONS.associateWith { e -> signal.map { it[e] ?: 0.0 }.average() }
        val topNonNeutral = avg.entries.filter { it.key != "neutral" }.maxByOrNull { it.value }
        val emotion = if (topNonNeutral != null && topNonNeutral.value >= SUPPRESS_THRESHOLD) topNonNeutral.key else "neutral"
        val sentiment = EmotionValence.sentimentOf(avg).coerceIn(-1.0, 1.0)
        val content = window.groupingBy { it.contentType }.eachCount().maxByOrNull { it.value }?.key ?: newest.contentType

        return newest.copy(
            emotion = emotion,
            emotionScore = avg[emotion] ?: 0.0,
            sentiment = sentiment,
            contentType = content,
            emotionScoresJson = EmotionScoresJson.encode(avg),
        )
    }
}
