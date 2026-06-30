package com.chiragdodia.screentrack

data class TextEmotion(
    val emotion: String,
    val confidence: Double,
    val sentiment: Double,
    val hadSignal: Boolean
)

/**
 * Pure, on-device lexicon sentiment with simple negation handling.
 * No Android imports — unit-tested on the JVM.
 */
object TextSentimentClassifier {

    private val NEGATORS = setOf("not", "no", "never", "n't", "cant", "cannot", "dont", "isnt", "wasnt")

    private val LEXICON: Map<String, List<String>> = mapOf(
        "joy" to listOf("happy", "joy", "love", "wonderful", "amazing", "great", "excited", "fun",
            "laugh", "smile", "celebrate", "congratulations", "awesome", "fantastic", "delighted",
            "pleased", "grateful", "thankful", "blessed", "enjoy", "lol", "haha", "glad", "win"),
        "sadness" to listOf("sad", "cry", "depressed", "unhappy", "alone", "lonely", "grief",
            "heartbreak", "disappoint", "regret", "sorrow", "tears", "suffer", "mourn", "hurt",
            "pain", "miserable", "hopeless", "miss"),
        "anger" to listOf("angry", "furious", "hate", "annoying", "frustrated", "outrage", "rage",
            "mad", "irritate", "disgusted", "offensive", "terrible", "awful", "worst", "unfair", "blame"),
        "fear" to listOf("scared", "afraid", "worried", "anxious", "nervous", "panic", "terror",
            "dread", "frighten", "horrified", "stress", "overwhelm", "danger", "threat", "crisis", "urgent"),
        "surprise" to listOf("wow", "surprising", "unexpected", "shocking", "unbelievable", "incredible",
            "astonishing", "remarkable", "sudden", "revealed", "exclusive")
    )

    private val SENTIMENT_OF: Map<String, Double> = mapOf(
        "joy" to 0.8, "surprise" to 0.3, "neutral" to 0.0,
        "sadness" to -0.5, "fear" to -0.6, "anger" to -0.7
    )

    fun classify(text: String): TextEmotion {
        if (text.isBlank()) return TextEmotion("neutral", 1.0, 0.0, hadSignal = false)
        val tokens = text.lowercase().split(Regex("[^a-z']+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return TextEmotion("neutral", 1.0, 0.0, hadSignal = false)

        val scores = LEXICON.keys.associateWith { 0.0 }.toMutableMap()
        var negativeFlips = 0.0
        tokens.forEachIndexed { i, tok ->
            val emotion = LEXICON.entries.firstOrNull { (_, words) -> words.any { tok.contains(it) } }?.key
            if (emotion != null) {
                val negated = (1..2).any { back -> tokens.getOrNull(i - back) in NEGATORS }
                if (negated) {
                    // a negated emotion word contributes the opposite polarity, counted as a flip
                    negativeFlips += 1.0
                } else {
                    scores[emotion] = scores.getValue(emotion) + 1.0
                }
            }
        }

        val totalHits = scores.values.sum() + negativeFlips
        if (totalHits == 0.0) return TextEmotion("neutral", 1.0, 0.0, hadSignal = false)

        val top = scores.maxByOrNull { it.value }!!
        // If flips dominate (e.g. "not happy"), fall to a negative read.
        if (top.value == 0.0 || negativeFlips > top.value) {
            return TextEmotion("sadness", (negativeFlips / totalHits).coerceIn(0.0, 1.0), -0.4, hadSignal = true)
        }
        val confidence = (top.value / totalHits).coerceIn(0.0, 1.0)
        val baseSentiment = SENTIMENT_OF.getValue(top.key)
        val sentiment = if (negativeFlips > 0) baseSentiment * 0.5 else baseSentiment
        return TextEmotion(top.key, confidence, sentiment, hadSignal = true)
    }
}
