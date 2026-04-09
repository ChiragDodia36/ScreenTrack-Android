package com.screentrack.mobile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "capture_results")
data class CaptureResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val emotion: String,
    val emotionScore: Double,
    val sentiment: Double,
    val contentType: String,
    val contentConfidence: Double,
    val textSnippet: String
)
