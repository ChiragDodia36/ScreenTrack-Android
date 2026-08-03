package com.chiragdodia.screentrack

/**
 * The text-side emotion result: the model's 28 per-emotion scores plus derived
 * dominant emotion and sentiment. `hadSignal` is false when there was no text.
 */
data class TextEmotion(
    val scores: EmotionScores,
    val hadSignal: Boolean,
) {
    val emotion: String get() = scores.maxByOrNull { it.value }?.key ?: "neutral"
    val confidence: Double get() = scores.maxByOrNull { it.value }?.value ?: 0.0
    val sentiment: Double get() = EmotionValence.sentimentOf(scores)
}
