package com.chiragdodia.screentrack

data class ContentResult(
    val contentType: String,
    val confidence: Double,
    val hadSignal: Boolean
)

/** Pure content-category classifier over OCR text. No Android imports. */
object ContentClassifier {

    private val CATEGORIES: Map<String, List<String>> = mapOf(
        "social_media" to listOf("tweet", "retweet", "instagram", "tiktok", "facebook", "follow",
            "story", "reel", "comment", "feed", "hashtag", "viral", "dm"),
        "messaging" to listOf("message", "chat", "reply", "inbox", "typing", "delivered", "read receipt",
            "whatsapp", "imessage", "text message"),
        "news" to listOf("breaking", "headline", "report", "journalist", "published", "reuters", "bbc",
            "cnn", "article", "politics", "election"),
        "shopping" to listOf("checkout", "cart", "buy now", "add to cart", "order", "discount", "sale",
            "shipping", "amazon", "price"),
        "work" to listOf("meeting", "spreadsheet", "deadline", "email", "calendar", "project", "slack",
            "zoom", "document", "report due"),
        "gaming" to listOf("respawn", "level", "score", "coins", "achievement", "quest", "imposter",
            "among us", "fortnite", "minecraft", "game over", "xp", "loadout"),
        "entertainment" to listOf("episode", "season", "movie", "trailer", "netflix", "stream",
            "subscribe", "playlist", "music video", "watch now"),
        "health" to listOf("calories", "workout", "exercise", "steps", "heart rate", "meditation",
            "sleep", "nutrition", "wellness"),
        "education" to listOf("lecture", "chapter", "homework", "tutorial", "study", "exam", "course",
            "quiz", "assignment", "syllabus", "professor", "learn")
    )

    /** Stable column order for the CSV: all categories + the "general" fallback. */
    val CATEGORY_NAMES: List<String> = CATEGORIES.keys.toList() + "general"

    fun classify(text: String): ContentResult {
        if (text.isBlank()) return ContentResult("general", 1.0, hadSignal = false)
        val lower = text.lowercase()
        val scores = CATEGORIES.mapValues { (_, words) -> words.count { lower.contains(it) }.toDouble() }
        val top = scores.maxByOrNull { it.value }
        val total = scores.values.sum()
        if (top == null || top.value == 0.0) return ContentResult("general", 1.0, hadSignal = false)
        return ContentResult(top.key, (top.value / total).coerceIn(0.0, 1.0), hadSignal = true)
    }
}
