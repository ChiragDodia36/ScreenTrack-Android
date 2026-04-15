package com.chiragdodia.screentrack

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FrameUploader {
    private const val MAX_RETRIES = 3
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()

    suspend fun uploadFrame(imageBytes: ByteArray, backendUrl: String, apiKey: String = ""): UploadResult {
        val endpoint = backendUrl.trimEnd('/') + "/api/upload_screenshot"

        var attempt = 0
        var delayMs = 1000L
        var lastError: String? = null
        while (attempt < MAX_RETRIES) {
            attempt++
            try {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        "screen.jpg",
                        imageBytes.toRequestBody("image/jpeg".toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .header("Connection", "keep-alive")
                    .apply { if (apiKey.isNotBlank()) header("X-API-Key", apiKey) }
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "Upload failed: HTTP ${response.code}"
                        if (attempt >= MAX_RETRIES) return UploadResult(null, lastError)
                    } else {
                        val responseBody = response.body?.string() ?: return UploadResult(null, "Empty response")
                        val json = JSONObject(responseBody)
                        if (!json.optString("status").equals("success", ignoreCase = true)) {
                            val message = json.optString("detail", "Backend error")
                            return UploadResult(null, message)
                        }
                        val analysis = json.optJSONObject("analysis") ?: return UploadResult(null, "Missing analysis")
                        val validEmotions = setOf("joy", "sadness", "anger", "fear", "disgust", "surprise", "neutral")
                        val validContentTypes = setOf(
                            "appearance-focused", "inspirational", "humorous", "educational",
                            "news", "social_media", "messaging", "shopping", "work", "entertainment",
                            "gaming", "health", "food", "travel", "tech", "sports", "arts", "general", "unknown"
                        )
                        val rawEmotion = analysis.optString("emotion", "unknown")
                        val safeEmotion = if (rawEmotion in validEmotions) rawEmotion else "unknown"
                        val rawContent = analysis.optString("content_type", "unknown")
                        val safeContent = if (rawContent in validContentTypes) rawContent else "unknown"
                        return UploadResult(
                            AnalysisResult(
                                emotion = safeEmotion,
                                emotionConfidence = analysis.optDouble("emotion_confidence", 0.0),
                                sentimentScore = analysis.optDouble("sentiment_score", 0.0),
                                contentType = safeContent,
                                contentConfidence = analysis.optDouble("content_confidence", 0.0),
                                textPreview = analysis.optString("text_preview", "")
                            ),
                            null
                        )
                    }
                }
            } catch (ex: java.net.SocketTimeoutException) {
                lastError = "Backend processing timed out (attempt $attempt/$MAX_RETRIES)"
                if (attempt >= MAX_RETRIES) {
                    return UploadResult(null, lastError)
                }
            } catch (ex: java.io.IOException) {
                lastError = "Connection error: ${ex.message} (attempt $attempt/$MAX_RETRIES)"
                if (attempt >= MAX_RETRIES) {
                    return UploadResult(null, lastError)
                }
            } catch (ex: Exception) {
                lastError = "${ex.message} (attempt $attempt/$MAX_RETRIES)"
                if (attempt >= MAX_RETRIES) {
                    return UploadResult(null, lastError)
                }
            }
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(8000L)
        }
        return UploadResult(null, lastError ?: "Upload failed after $MAX_RETRIES attempts")
    }
}

data class AnalysisResult(
    val emotion: String,
    val emotionConfidence: Double,
    val sentimentScore: Double,
    val contentType: String,
    val contentConfidence: Double,
    val textPreview: String
)

data class UploadResult(
    val analysis: AnalysisResult?,
    val error: String?
)
