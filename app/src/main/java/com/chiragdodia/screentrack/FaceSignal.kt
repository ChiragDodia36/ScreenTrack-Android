package com.chiragdodia.screentrack

data class FaceSignal(val sentiment: Double, val hadSignal: Boolean)

/** Pure mapping from a face's smiling probability to a sentiment contribution. */
object FaceSignals {
    fun fromSmilingProbability(p: Float?): FaceSignal {
        if (p == null) return FaceSignal(0.0, hadSignal = false)
        // Map [0,1] smiling probability to [-0.6, +0.8] sentiment.
        val sentiment = when {
            p >= 0.6f -> 0.8 * ((p - 0.6) / 0.4)
            p <= 0.3f -> -0.6 * ((0.3 - p) / 0.3)
            else -> 0.0
        }
        return FaceSignal(sentiment.coerceIn(-0.6, 0.8), hadSignal = true)
    }
}
