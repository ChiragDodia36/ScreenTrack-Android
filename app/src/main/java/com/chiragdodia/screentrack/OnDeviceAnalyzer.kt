package com.chiragdodia.screentrack

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnDeviceAnalyzer {

    data class Result(
        val emotion: String,
        val emotionConfidence: Double,
        val sentiment: Double,
        val contentType: String,
        val contentConfidence: Double,
        val textSnippet: String
    )

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(bitmap: Bitmap): Result {
        val text = extractText(bitmap)
        val (emotion, emotionConf) = classifyEmotion(text)
        val sentiment = sentimentFromEmotion(emotion)
        val (contentType, contentConf) = classifyContent(text)
        return Result(emotion, emotionConf, sentiment, contentType, contentConf, text.take(200))
    }

    private suspend fun extractText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        Tasks.await(textRecognizer.process(inputImage)).text
    }

    private fun classifyEmotion(text: String): Pair<String, Double> {
        val lower = text.lowercase()
        val scores = mapOf(
            "joy"      to JOY_WORDS.count { lower.contains(it) }.toDouble(),
            "sadness"  to SADNESS_WORDS.count { lower.contains(it) }.toDouble(),
            "anger"    to ANGER_WORDS.count { lower.contains(it) }.toDouble(),
            "fear"     to FEAR_WORDS.count { lower.contains(it) }.toDouble(),
            "surprise" to SURPRISE_WORDS.count { lower.contains(it) }.toDouble()
        )
        val top = scores.maxByOrNull { it.value }
        if (top == null || top.value == 0.0) return "neutral" to 1.0
        val total = scores.values.sum().coerceAtLeast(1.0)
        return top.key to (top.value / total).coerceIn(0.0, 1.0)
    }

    private fun sentimentFromEmotion(emotion: String): Double = when (emotion) {
        "joy"      ->  0.8
        "surprise" ->  0.3
        "neutral"  ->  0.0
        "sadness"  -> -0.5
        "fear"     -> -0.6
        "anger"    -> -0.7
        else       ->  0.0
    }

    private fun classifyContent(text: String): Pair<String, Double> {
        val lower = text.lowercase()
        val categories = mapOf(
            "social_media"  to listOf("tweet", "retweet", "instagram", "tiktok", "facebook", "like", "follow", "share", "post", "story", "reel"),
            "messaging"     to listOf("message", "chat", "reply", "dm", "inbox", "typing", "send", "delivered", "read"),
            "news"          to listOf("breaking", "headline", "report", "journalist", "published", "reuters", "bbc", "cnn", "article"),
            "shopping"      to listOf("checkout", "cart", "buy now", "add to cart", "order", "price", "discount", "sale", "shipping"),
            "work"          to listOf("meeting", "spreadsheet", "deadline", "email", "calendar", "project", "task", "slack", "zoom", "document"),
            "gaming"        to listOf("score", "level", "game", "play", "lives", "coins", "achievement", "quest", "respawn"),
            "entertainment" to listOf("watch", "episode", "season", "movie", "trailer", "netflix", "youtube", "stream", "subscribe"),
            "health"        to listOf("calories", "workout", "exercise", "steps", "heart rate", "meditation", "sleep", "nutrition")
        )
        val scores = categories.mapValues { (_, words) -> words.count { lower.contains(it) }.toDouble() }
        val top = scores.maxByOrNull { it.value }
        if (top == null || top.value == 0.0) return "general" to 1.0
        val total = scores.values.sum().coerceAtLeast(1.0)
        return top.key to (top.value / total).coerceIn(0.0, 1.0)
    }

    fun close() = textRecognizer.close()

    companion object {
        private val JOY_WORDS = listOf(
            "happy", "joy", "love", "wonderful", "amazing", "great", "excited", "fun",
            "laugh", "smile", "celebrate", "congratulations", "awesome", "fantastic",
            "delighted", "pleased", "grateful", "thankful", "blessed", "enjoy", "lol", "haha"
        )
        private val SADNESS_WORDS = listOf(
            "sad", "cry", "depressed", "unhappy", "alone", "miss", "lonely", "grief",
            "heartbreak", "disappoint", "regret", "sorry", "sorrow", "tears", "suffer",
            "mourn", "lost", "hurt", "pain", "miserable", "hopeless"
        )
        private val ANGER_WORDS = listOf(
            "angry", "furious", "hate", "annoying", "frustrated", "outrage", "rage",
            "mad", "irritate", "disgusted", "offensive", "stupid", "terrible",
            "awful", "worst", "absurd", "unfair", "blame", "complain", "protest"
        )
        private val FEAR_WORDS = listOf(
            "scared", "afraid", "worried", "anxious", "nervous", "panic", "terror",
            "dread", "frighten", "horrified", "stress", "overwhelm", "danger",
            "threat", "warning", "alert", "crisis", "disaster", "urgent", "emergency"
        )
        private val SURPRISE_WORDS = listOf(
            "wow", "surprising", "unexpected", "shocking", "unbelievable", "incredible",
            "astonishing", "remarkable", "sudden", "breaking", "just announced",
            "revealed", "discovered", "exclusive", "never before"
        )
    }
}
