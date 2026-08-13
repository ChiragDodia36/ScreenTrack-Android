package com.chiragdodia.screentrack

data class ContentResult(
    val contentType: String,
    val confidence: Double,
    val hadSignal: Boolean
)

/** Pure content-category classifier over OCR text. No Android imports. */
object ContentClassifier {

    private const val FALLBACK = "general_or_miscellaneous"

    private val CATEGORIES: Map<String, List<String>> = mapOf(
        "news_articles_current" to listOf("breaking", "headline", "report", "journalist", "published",
            "reuters", "bbc", "cnn", "article", "news", "weather", "storm", "politics", "election", "live"),
        "humorous_content" to listOf("funny", "joke", "jokes", "meme", "memes", "comedy", "hilarious",
            "prank", "lol", "humor", "humour", "fail", "fails", "funniest", "laugh", "try not to laugh"),
        "shopping_ecommerce_products" to listOf("checkout", "cart", "buy now", "add to cart", "order",
            "discount", "sale", "shipping", "amazon", "price", "deal", "deals", "shop", "store", "product"),
        "videos" to listOf("youtube", "video", "watch", "subscribe", "views", "channel", "stream",
            "episode", "playlist", "shorts", "likes", "watch?v="),
        "food_recipes_cooking" to listOf("recipe", "recipes", "cooking", "cook", "food", "ingredients",
            "bake", "kitchen", "meal", "dish", "restaurant", "cuisine", "tasty", "delicious"),
        "work_related" to listOf("meeting", "spreadsheet", "deadline", "email", "calendar", "project",
            "slack", "zoom", "document", "presentation", "task", "report due", "workspace"),
    )

    /** Stable column order for the CSV: the 6 keyword categories + the general fallback. */
    val CATEGORY_NAMES: List<String> = CATEGORIES.keys.toList() + FALLBACK

    fun classify(text: String): ContentResult {
        if (text.isBlank()) return ContentResult(FALLBACK, 1.0, hadSignal = false)
        val lower = text.lowercase()
        val scores = CATEGORIES.mapValues { (_, words) -> words.count { lower.contains(it) }.toDouble() }
        val top = scores.maxByOrNull { it.value }
        val total = scores.values.sum()
        if (top == null || top.value == 0.0) return ContentResult(FALLBACK, 1.0, hadSignal = false)
        return ContentResult(top.key, (top.value / total).coerceIn(0.0, 1.0), hadSignal = true)
    }
}
