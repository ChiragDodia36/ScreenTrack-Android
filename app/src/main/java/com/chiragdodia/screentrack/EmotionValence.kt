package com.chiragdodia.screentrack

/**
 * Valence (-1..+1) for each GoEmotions label, used to collapse the 28 emotion
 * scores into a single sentiment value and into positive/negative/neutral buckets.
 * Pure — unit-tested.
 */
object EmotionValence {

    val TABLE: Map<String, Double> = mapOf(
        // positive
        "admiration" to 0.6, "amusement" to 0.7, "approval" to 0.5, "caring" to 0.6,
        "desire" to 0.3, "excitement" to 0.8, "gratitude" to 0.8, "joy" to 0.9,
        "love" to 0.9, "optimism" to 0.7, "pride" to 0.7, "relief" to 0.6,
        // mildly positive / ambiguous-lean
        "curiosity" to 0.2, "realization" to 0.1,
        // neutral / ambiguous
        "neutral" to 0.0, "surprise" to 0.0, "confusion" to -0.1,
        // negative
        "anger" to -0.8, "annoyance" to -0.5, "disappointment" to -0.6,
        "disapproval" to -0.5, "disgust" to -0.7, "embarrassment" to -0.5,
        "fear" to -0.7, "grief" to -0.9, "nervousness" to -0.5, "remorse" to -0.6,
        "sadness" to -0.7
    )

    fun valence(emotion: String): Double = TABLE[emotion] ?: 0.0

    /** Score-weighted average valence across the emotion scores. 0 if no signal. */
    fun sentimentOf(scores: EmotionScores): Double {
        val total = scores.values.sum()
        if (total <= 0.0) return 0.0
        return scores.entries.sumOf { (e, s) -> s * valence(e) } / total
    }
}
