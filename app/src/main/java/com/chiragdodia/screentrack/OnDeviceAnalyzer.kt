package com.chiragdodia.screentrack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates on-device ML (OCR + 27-emotion text model + FER face model) and
 * delegates all decisions to the pure classifiers. No screen image is ever
 * stored or transmitted.
 */
class OnDeviceAnalyzer(context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val emotionModel = EmotionModel(context)
    private val faceModel = FaceEmotionModel(context)

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    suspend fun analyze(bitmap: Bitmap): AnalysisResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = runCatching { Tasks.await(textRecognizer.process(image)).text }.getOrDefault("")
        val faceBox: Rect? = runCatching {
            Tasks.await(faceDetector.process(image))
                .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                ?.boundingBox
        }.getOrNull()

        val scores = emotionModel.scores(text)
        val textEmotion = TextEmotion(scores, hadSignal = scores.isNotEmpty())
        val content = ContentClassifier.classify(text)
        val face = faceEmotion(bitmap, faceBox)
        AnalysisCombiner.combine(textEmotion, face, content)
    }

    private fun faceEmotion(bitmap: Bitmap, box: Rect?): FaceEmotion {
        val crop = box?.let { cropFace(bitmap, it) } ?: return FaceEmotion(emptyMap(), hadSignal = false)
        val fer = runCatching { faceModel.scores(crop) }.getOrNull()
        crop.recycle()
        return if (fer != null) FaceEmotion(FaceEmotionMap.toGoEmotions(fer), hadSignal = true)
        else FaceEmotion(emptyMap(), hadSignal = false)
    }

    private fun cropFace(bitmap: Bitmap, box: Rect): Bitmap? {
        val left = box.left.coerceAtLeast(0)
        val top = box.top.coerceAtLeast(0)
        val right = box.right.coerceAtMost(bitmap.width)
        val bottom = box.bottom.coerceAtMost(bitmap.height)
        if (right - left < 8 || bottom - top < 8) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    fun close() {
        textRecognizer.close()
        faceDetector.close()
        emotionModel.close()
        faceModel.close()
    }
}
