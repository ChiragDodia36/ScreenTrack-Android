package com.chiragdodia.screentrack

/**
 * Collapses the 28 GoEmotions model outputs into the 6 Ekman basic emotions
 * plus neutral, using the GoEmotions paper's official Ekman grouping. Grouping a
 * well-trained 27-class model is more reliable than a native 6-class model. Pure.
 */
object EkmanMap {

    /** The 6 Ekman basics + neutral, in display / CSV-column order. */
    val EKMAN_EMOTIONS: List<String> = listOf(
        "anger", "disgust", "fear", "joy", "sadness", "surprise", "neutral"
    )

    private val GO_TO_EKMAN: Map<String, String> = mapOf(
        "anger" to "anger", "annoyance" to "anger", "disapproval" to "anger",
        "disgust" to "disgust",
        "fear" to "fear", "nervousness" to "fear",
        "joy" to "joy", "amusement" to "joy", "approval" to "joy", "excitement" to "joy",
        "gratitude" to "joy", "love" to "joy", "optimism" to "joy", "relief" to "joy",
        "pride" to "joy", "admiration" to "joy", "desire" to "joy", "caring" to "joy",
        "sadness" to "sadness", "disappointment" to "sadness", "embarrassment" to "sadness",
        "grief" to "sadness", "remorse" to "sadness",
        "surprise" to "surprise", "realization" to "surprise", "confusion" to "surprise",
        "curiosity" to "surprise",
        "neutral" to "neutral",
    )

    /** Sum the 28 GoEmotions scores into the 7 Ekman buckets. */
    fun toEkman(goScores: EmotionScores): EmotionScores {
        val out = EKMAN_EMOTIONS.associateWith { 0.0 }.toMutableMap()
        for ((emo, score) in goScores) {
            val bucket = GO_TO_EKMAN[emo] ?: continue
            out[bucket] = out.getValue(bucket) + score
        }
        return out
    }
}
