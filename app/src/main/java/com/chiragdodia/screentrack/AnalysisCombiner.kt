package com.chiragdodia.screentrack

data class AnalysisResult(
    val emotion: String,
    val emotionConfidence: Double,
    val sentiment: Double,
    val contentType: String,
    val contentConfidence: Double,
    val emotionScores: EmotionScores,
)

/** Pure merge of text + face emotion scores into the persisted analysis result. */
object AnalysisCombiner {

    private const val TEXT_WEIGHT = 0.6
    private const val FACE_WEIGHT = 0.4

    fun combine(text: TextEmotion, face: FaceEmotion, content: ContentResult): AnalysisResult {
        val scores28 = blend(text, face)
        if (scores28.isEmpty()) {
            return AnalysisResult("undetermined", 0.0, 0.0, content.contentType, content.confidence, emptyMap())
        }
        // Sentiment from the full 28-emotion valence (more precise), then collapse
        // the emotion label + stored scores to the 6 Ekman basics + neutral.
        val sentiment = EmotionValence.sentimentOf(scores28).coerceIn(-1.0, 1.0)
        val ekman = EkmanMap.toEkman(scores28)
        val top = ekman.maxByOrNull { it.value }!!
        return AnalysisResult(top.key, top.value, sentiment, content.contentType, content.confidence, ekman)
    }

    /**
     * Blend on the emotions the face model can see (text*0.6 + face*0.4); every
     * other GoEmotions label comes from text alone. Falls back to whichever side
     * has a signal.
     */
    private fun blend(text: TextEmotion, face: FaceEmotion): EmotionScores = when {
        !text.hadSignal && !face.hadSignal -> emptyMap()
        !face.hadSignal -> text.scores
        !text.hadSignal -> face.scores
        else -> GO_EMOTIONS.associateWith { e ->
            val t = text.scores[e] ?: 0.0
            val f = face.scores[e] ?: 0.0
            if (e in FaceEmotionMap.MAPPED_GO) t * TEXT_WEIGHT + f * FACE_WEIGHT else t
        }
    }
}
