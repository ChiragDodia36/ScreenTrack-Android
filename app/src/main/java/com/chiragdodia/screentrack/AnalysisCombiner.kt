package com.chiragdodia.screentrack

data class AnalysisResult(
    val emotion: String,
    val emotionConfidence: Double,
    val sentiment: Double,
    val contentType: String,
    val contentConfidence: Double,
    val emotionScores: EmotionScores,
)

/** Pure merge of text + face signals into the persisted analysis result. */
object AnalysisCombiner {

    private const val TEXT_WEIGHT = 0.6
    private const val FACE_WEIGHT = 0.4

    fun combine(text: TextEmotion, face: FaceSignal, content: ContentResult): AnalysisResult {
        val emotion: String
        val emotionConfidence: Double
        val sentiment: Double
        val scores: EmotionScores

        when {
            !text.hadSignal && !face.hadSignal -> {
                emotion = "undetermined"; emotionConfidence = 0.0; sentiment = 0.0; scores = emptyMap()
            }
            text.hadSignal && face.hadSignal -> {
                sentiment = (text.sentiment * TEXT_WEIGHT + face.sentiment * FACE_WEIGHT).coerceIn(-1.0, 1.0)
                emotion = labelFor(text, sentiment)
                emotionConfidence = text.confidence
                scores = text.scores
            }
            text.hadSignal -> {
                sentiment = text.sentiment; emotion = text.emotion
                emotionConfidence = text.confidence; scores = text.scores
            }
            else -> { // face only
                sentiment = face.sentiment
                emotion = if (face.sentiment >= 0) "joy" else "sadness"
                emotionConfidence = kotlin.math.abs(face.sentiment).coerceIn(0.0, 1.0)
                scores = emptyMap()
            }
        }
        return AnalysisResult(emotion, emotionConfidence, sentiment, content.contentType, content.confidence, scores)
    }

    private fun labelFor(text: TextEmotion, blended: Double): String {
        // Prefer the text's dominant emotion, unless the face flips overall polarity hard.
        return if (text.sentiment >= 0 && blended < -0.2) "sadness"
        else if (text.sentiment < 0 && blended > 0.2) "joy"
        else text.emotion
    }
}
