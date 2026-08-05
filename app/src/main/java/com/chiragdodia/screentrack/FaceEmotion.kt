package com.chiragdodia.screentrack

/** Face-side emotion result: FER emotions mapped into the GoEmotions space. */
data class FaceEmotion(
    val scores: EmotionScores,
    val hadSignal: Boolean,
)
