package com.chiragdodia.screentrack

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates on-device ML (OCR + 27-emotion model + face) and delegates all
 * decisions to the pure classifiers. No screen image is ever stored or transmitted.
 */
class OnDeviceAnalyzer(context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val emotionModel = EmotionModel(context)

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // enables smiling probability
            .build()
    )

    suspend fun analyze(bitmap: Bitmap): AnalysisResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = runCatching { Tasks.await(textRecognizer.process(image)).text }.getOrDefault("")
        val smiling: Float? = runCatching {
            Tasks.await(faceDetector.process(image)).maxByOrNull { it.smilingProbability ?: -1f }?.smilingProbability
        }.getOrNull()

        val scores = emotionModel.scores(text)
        val textEmotion = TextEmotion(scores, hadSignal = scores.isNotEmpty())
        val content = ContentClassifier.classify(text)
        val face = FaceSignals.fromSmilingProbability(smiling)
        AnalysisCombiner.combine(textEmotion, face, content)
    }

    fun close() {
        textRecognizer.close()
        faceDetector.close()
        emotionModel.close()
    }
}
