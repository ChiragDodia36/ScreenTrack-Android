package com.chiragdodia.screentrack

/** A map of GoEmotions label -> score (0..1). */
typealias EmotionScores = Map<String, Double>

/**
 * The 28 GoEmotions labels in the exact order of the model's output logits
 * (id2label from SamLowe/roberta-base-go_emotions). Order is load-bearing:
 * logit[i] corresponds to GO_EMOTIONS[i].
 */
val GO_EMOTIONS: List<String> = listOf(
    "admiration", "amusement", "anger", "annoyance", "approval", "caring",
    "confusion", "curiosity", "desire", "disappointment", "disapproval",
    "disgust", "embarrassment", "excitement", "fear", "gratitude", "grief",
    "joy", "love", "nervousness", "optimism", "pride", "realization", "relief",
    "remorse", "sadness", "surprise", "neutral"
)
