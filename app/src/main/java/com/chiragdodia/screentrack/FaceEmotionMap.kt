package com.chiragdodia.screentrack

/**
 * Maps the FER+ model's 8 facial emotions into the 28 GoEmotions labels.
 * Pure — unit-tested. Only the 8 mapped GoEmotions get a face score; the rest
 * come from text alone.
 */
object FaceEmotionMap {

    /** FER+ output order (emotion-ferplus-8). Index-aligned with the model logits. */
    val FER_LABELS: List<String> = listOf(
        "neutral", "happiness", "surprise", "sadness", "anger", "disgust", "fear", "contempt"
    )

    /** FER emotion -> GoEmotions label. */
    private val FER_TO_GO: Map<String, String> = mapOf(
        "neutral" to "neutral",
        "happiness" to "joy",
        "surprise" to "surprise",
        "sadness" to "sadness",
        "anger" to "anger",
        "disgust" to "disgust",
        "fear" to "fear",
        "contempt" to "disapproval",
    )

    /** The GoEmotions labels the face model can contribute to. */
    val MAPPED_GO: Set<String> = FER_TO_GO.values.toSet()

    /** FER softmax scores (in FER_LABELS order) -> GoEmotions score map. */
    fun toGoEmotions(ferScores: List<Double>): EmotionScores =
        FER_LABELS.indices.associate { FER_TO_GO.getValue(FER_LABELS[it]) to ferScores[it] }
}
