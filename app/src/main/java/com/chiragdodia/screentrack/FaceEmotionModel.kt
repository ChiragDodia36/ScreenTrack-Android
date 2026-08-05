package com.chiragdodia.screentrack

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * On-device facial-emotion model (emotion-ferplus-8). Input: a cropped face,
 * resized to 64x64 grayscale. Output: softmax over the 8 FER emotions
 * (FaceEmotionMap.FER_LABELS order). Impure I/O — verified on-device.
 */
class FaceEmotionModel(context: Context) {

    private val appContext = context.applicationContext
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private val session: OrtSession by lazy {
        env.createSession(
            appContext.assets.open("fer_model.onnx").readBytes(),
            OrtSession.SessionOptions()
        )
    }

    /** 8 softmax scores in FaceEmotionMap.FER_LABELS order. */
    fun scores(face: Bitmap): List<Double> {
        val resized = Bitmap.createScaledBitmap(face, 64, 64, true)
        val px = IntArray(64 * 64)
        resized.getPixels(px, 0, 64, 0, 0, 64, 64)
        if (resized != face) resized.recycle()

        val input = FloatArray(64 * 64)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            input[i] = 0.299f * r + 0.587f * g + 0.114f * b // grayscale, raw 0..255
        }

        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 1, 64, 64))
        val out = session.run(mapOf("Input3" to tensor))
        @Suppress("UNCHECKED_CAST")
        val logits = (out.get(0).value as Array<FloatArray>)[0]
        tensor.close(); out.close()
        return softmax(logits)
    }

    fun close() {
        runCatching { session.close() }
    }

    private fun softmax(x: FloatArray): List<Double> {
        val max = x.maxOrNull() ?: 0f
        val exps = x.map { exp((it - max).toDouble()) }
        val sum = exps.sum().coerceAtLeast(1e-9)
        return exps.map { it / sum }
    }
}
