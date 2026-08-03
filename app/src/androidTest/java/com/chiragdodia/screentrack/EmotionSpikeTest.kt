package com.chiragdodia.screentrack

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.LongBuffer
import kotlin.math.exp

/**
 * Phase 0.5 spike: run the GoEmotions ONNX model + embedded tokenizer on the
 * physical device. Logs per-inference latency and the top emotions.
 * Run: ./gradlew connectedDebugAndroidTest ; adb logcat -d -s SPIKE
 */
@RunWith(AndroidJUnit4::class)
class EmotionSpikeTest {

    private val labels = listOf(
        "admiration", "amusement", "anger", "annoyance", "approval", "caring",
        "confusion", "curiosity", "desire", "disappointment", "disapproval",
        "disgust", "embarrassment", "excitement", "fear", "gratitude", "grief",
        "joy", "love", "nervousness", "optimism", "pride", "realization", "relief",
        "remorse", "sadness", "surprise", "neutral"
    )

    @Test
    fun runsOnDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val env = OrtEnvironment.getEnvironment()

        val tokBytes = ctx.assets.open("tokenizer.onnx").readBytes()
        val mdlBytes = ctx.assets.open("emotion_model.onnx").readBytes()

        val tokOpts = OrtSession.SessionOptions().apply {
            registerCustomOpLibrary(OrtxPackage.getLibraryPath())
        }
        val tok = env.createSession(tokBytes, tokOpts)
        val mdl = env.createSession(mdlBytes, OrtSession.SessionOptions())
        Log.i("SPIKE", "sessions created; model=${mdlBytes.size / 1_000_000}MB")

        val samples = listOf("I love this so much!", "this is terrifying", "so proud of you")
        var lovewins = false
        for (text in samples) {
            val t0 = SystemClock.elapsedRealtime()

            val strIn = OnnxTensor.createTensor(env, arrayOf(text), longArrayOf(1))
            val tokOut = tok.run(mapOf("input_text" to strIn))
            val ids = flat(tokOut.get("input_ids").get())
            val mask = flat(tokOut.get("attention_mask").get())

            val idsIn = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
            val maskIn = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, mask.size.toLong()))
            val out = mdl.run(mapOf("input_ids" to idsIn, "attention_mask" to maskIn))

            @Suppress("UNCHECKED_CAST")
            val logits = (out.get(0).value as Array<FloatArray>)[0]
            val scores = logits.map { 1f / (1f + exp(-it)) }
            val top = labels.indices.sortedByDescending { scores[it] }.take(3)
                .map { labels[it] to "%.2f".format(scores[it]) }
            val ms = SystemClock.elapsedRealtime() - t0
            Log.i("SPIKE", "[$ms ms] \"$text\" -> $top")
            if (text.startsWith("I love") && top.first().first == "love") lovewins = true

            strIn.close(); idsIn.close(); maskIn.close(); tokOut.close(); out.close()
        }
        tok.close(); mdl.close()
        assertTrue("expected 'love' to top the love sample", lovewins)
    }

    /** Flatten a 1-D or 2-D int64 ONNX tensor value to a flat LongArray. */
    private fun flat(v: OnnxValue): LongArray = when (val a = v.value) {
        is LongArray -> a
        is Array<*> -> (a as Array<LongArray>).fold(LongArray(0)) { acc, row -> acc + row }
        else -> error("unexpected tensor type ${a?.javaClass}")
    }
}
