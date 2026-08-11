package com.chiragdodia.screentrack

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import kotlin.math.exp

/**
 * On-device GoEmotions (RoBERTa) classifier via ONNX Runtime. Takes raw text
 * (the embedded tokenizer handles tokenization) and returns a score per emotion.
 * Impure I/O — verified on-device (see EmotionSpikeTest), not JVM-unit-tested.
 */
class EmotionModel(context: Context) {

    private val appContext = context.applicationContext
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private val tokenizer: OrtSession by lazy {
        val opts = OrtSession.SessionOptions().apply {
            registerCustomOpLibrary(OrtxPackage.getLibraryPath())
        }
        env.createSession(ModelAssets.path(appContext, "tokenizer.onnx"), opts)
    }
    private val model: OrtSession by lazy {
        // Load from a file path (memory-mapped) — NOT readBytes(), which OOMs
        // reading the 125 MB model onto the Java heap.
        env.createSession(ModelAssets.path(appContext, "emotion_model.onnx"), OrtSession.SessionOptions())
    }

    /** Emotion -> score (0..1). Empty map when there's no text signal. */
    suspend fun scores(text: String): EmotionScores = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext emptyMap()

        val strIn = OnnxTensor.createTensor(env, arrayOf(text.take(MAX_CHARS)), longArrayOf(1))
        val tokOut = tokenizer.run(mapOf("input_text" to strIn))
        val ids = flat(tokOut.get("input_ids").get()).capTo(MAX_TOKENS)
        val mask = flat(tokOut.get("attention_mask").get()).capTo(MAX_TOKENS)

        val idsIn = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
        val maskIn = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, mask.size.toLong()))
        val out = model.run(mapOf("input_ids" to idsIn, "attention_mask" to maskIn))

        @Suppress("UNCHECKED_CAST")
        val logits = (out.get(0).value as Array<FloatArray>)[0]
        val result = GO_EMOTIONS.indices.associate {
            GO_EMOTIONS[it] to 1.0 / (1.0 + exp(-logits[it].toDouble()))
        }

        strIn.close(); idsIn.close(); maskIn.close(); tokOut.close(); out.close()
        result
    }

    fun close() {
        runCatching { tokenizer.close() }
        runCatching { model.close() }
    }

    private fun flat(v: OnnxValue): LongArray = when (val a = v.value) {
        is LongArray -> a
        is Array<*> -> {
            @Suppress("UNCHECKED_CAST")
            (a as Array<LongArray>).fold(LongArray(0)) { acc, row -> acc + row }
        }
        else -> error("unexpected tensor type ${a?.javaClass}")
    }

    private fun LongArray.capTo(n: Int): LongArray = if (size > n) copyOf(n) else this

    companion object {
        // RoBERTa hard-caps at 512 positions; 128 is ample for screen fragments and keeps latency low.
        private const val MAX_TOKENS = 128
        private const val MAX_CHARS = 2000
    }
}
