package com.chiragdodia.screentrack

data class AnalysisResult(
    val emotion: String,
    val emotionConfidence: Double,
    val sentiment: Double,
    val contentType: String,
    val contentConfidence: Double
)

/** Pure merge of text + face signals into the persisted analysis result. */
object AnalysisCombiner {

    private const val TEXT_WEIGHT = 0.6
    private const val FACE_WEIGHT = 0.4

    fun combine(text: TextEmotion, face: FaceSignal, content: ContentResult): AnalysisResult {
        val emotion: String
        val emotionConfidence: Double
        val sentiment: Double

        when {
            !text.hadSignal && !face.hadSignal -> {
                emotion = "undetermined"; emotionConfidence = 0.0; sentiment = 0.0
            }
            text.hadSignal && face.hadSignal -> {
                sentiment = (text.sentiment * TEXT_WEIGHT + face.sentiment * FACE_WEIGHT)
                    .coerceIn(-1.0, 1.0)
                emotion = labelFor(text, sentiment)
                emotionConfidence = ((text.confidence + 1.0) / 2.0).coerceIn(0.0, 1.0)
            }
            text.hadSignal -> {
                sentiment = text.sentiment; emotion = text.emotion; emotionConfidence = text.confidence
            }
            else -> { // face only
                sentiment = face.sentiment
                emotion = if (face.sentiment >= 0) "joy" else "sadness"
                emotionConfidence = kotlin.math.abs(face.sentiment).coerceIn(0.0, 1.0)
            }
        }
        return AnalysisResult(emotion, emotionConfidence, sentiment, content.contentType, content.confidence)
    }

    private fun labelFor(text: TextEmotion, blended: Double): String {
        // Prefer the text's discrete emotion, unless the face flips overall polarity hard.
        return if (text.sentiment >= 0 && blended < -0.2) "sadness"
        else if (text.sentiment < 0 && blended > 0.2) "joy"
        else text.emotion
    }
}
