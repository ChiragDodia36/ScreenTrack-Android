package com.screentrack.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val EmotionJoy = Color(0xFF4ADE80)
val EmotionSadness = Color(0xFF60A5FA)
val EmotionAnger = Color(0xFFF87171)
val EmotionFear = Color(0xFFA78BFA)
val EmotionSurprise = Color(0xFFFBBF24)
val EmotionDisgust = Color(0xFF94A3B8)
val EmotionNeutral = Color(0xFF6B7280)

val ContentPalette = listOf(
    Color(0xFF4ADE80),
    Color(0xFF60A5FA),
    Color(0xFFFBBF24),
    Color(0xFFF87171),
    Color(0xFFA78BFA),
    Color(0xFF2DD4BF),
    Color(0xFFFB923C),
    Color(0xFFE879F9),
    Color(0xFF38BDF8)
)

val PrimaryTeal = Color(0xFF00D4AA)
val SecondaryPurple = Color(0xFF8B5CF6)

private enum class ChartGranularity(val label: String, val bucketMs: Long) {
    ALL("All", 0L),
    MIN_15("15m", 15L * 60 * 1000),
    MIN_30("30m", 30L * 60 * 1000),
    HOUR_1("1h", 60L * 60 * 1000),
    HOUR_3("3h", 3L * 60 * 60 * 1000),
    HOUR_6("6h", 6L * 60 * 60 * 1000)
}

private data class SentimentBucket(
    val timestampMs: Long,
    val avgSentiment: Float,
    val dominantEmotion: String,
    val count: Int
)

private fun aggregateIntoBuckets(
    items: List<CaptureResultEntity>,
    granularity: ChartGranularity
): List<SentimentBucket> {
    if (items.isEmpty()) return emptyList()
    val sorted = items.sortedBy { it.timestampMs }

    if (granularity == ChartGranularity.ALL) {
        return sorted.map { SentimentBucket(it.timestampMs, it.sentiment.toFloat(), it.emotion, 1) }
    }

    val bucketMs = granularity.bucketMs
    val firstTs = sorted.first().timestampMs
    val grouped = sorted.groupBy { (it.timestampMs - firstTs) / bucketMs }

    return grouped.entries.sortedBy { it.key }.map { (_, bucketItems) ->
        val avgSent = bucketItems.map { it.sentiment }.average().toFloat()
        val midTs = bucketItems.map { it.timestampMs }.average().toLong()
        val topEmotion = bucketItems.groupingBy { it.emotion }.eachCount()
            .maxByOrNull { it.value }?.key ?: "neutral"
        SentimentBucket(midTs, avgSent, topEmotion, bucketItems.size)
    }
}

@Composable
fun SentimentLineChart(items: List<CaptureResultEntity>) {
    var selectedGranularity by remember { mutableStateOf(ChartGranularity.MIN_30) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Sentiment Timeline",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChartGranularity.values().forEach { gran ->
                val isSelected = gran == selectedGranularity
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) PrimaryTeal else Color(0xFF242836),
                    modifier = Modifier.clickable { selectedGranularity = gran }
                ) {
                    Text(
                        text = gran.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color(0xFF0F1118) else Color(0xFF9CA3AF),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        if (items.size < 2) {
            Text(
                "Not enough data yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        val buckets = remember(items, selectedGranularity) {
            aggregateIntoBuckets(items, selectedGranularity)
        }

        if (buckets.size < 2) {
            Text(
                "Not enough buckets for this granularity. Try a smaller interval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        val midlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${buckets.size} points",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9CA3AF)
            )
            val range = buckets.last().avgSentiment - buckets.first().avgSentiment
            val trendText = when {
                range > 0.1f -> "Trending up"
                range < -0.1f -> "Trending down"
                else -> "Stable"
            }
            val trendColor = when {
                range > 0.1f -> EmotionJoy
                range < -0.1f -> EmotionAnger
                else -> Color(0xFF9CA3AF)
            }
            Text(trendText, style = MaterialTheme.typography.labelSmall, color = trendColor, fontWeight = FontWeight.SemiBold)
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val w = size.width
            val h = size.height
            val minY = -1f
            val maxY = 1f
            val paddingLeft = 16f
            val chartWidth = w - paddingLeft

            for (i in 0..4) {
                val y = (i / 4f) * h
                drawLine(gridColor, Offset(paddingLeft, y), Offset(w, y), strokeWidth = 1f)
            }

            drawLine(midlineColor, Offset(paddingLeft, h / 2f), Offset(w, h / 2f), strokeWidth = 1.5f)

            val tickColor = Color(0xFF9CA3AF)
            val tickLen = 5f
            drawLine(tickColor, Offset(paddingLeft - tickLen, 0f), Offset(paddingLeft, 0f), strokeWidth = 1.5f)
            drawLine(tickColor, Offset(paddingLeft - tickLen, h / 2f), Offset(paddingLeft, h / 2f), strokeWidth = 1.5f)
            drawLine(tickColor, Offset(paddingLeft - tickLen, h), Offset(paddingLeft, h), strokeWidth = 1.5f)

            val path = Path()
            buckets.forEachIndexed { index, bucket ->
                val x = paddingLeft + if (buckets.size == 1) 0f else (index / (buckets.size - 1f)) * chartWidth
                val normalized = ((bucket.avgSentiment - minY) / (maxY - minY)).coerceIn(0f, 1f)
                val y = h - normalized * h
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(PrimaryTeal, SecondaryPurple)),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            buckets.forEachIndexed { index, bucket ->
                val x = paddingLeft + if (buckets.size == 1) 0f else (index / (buckets.size - 1f)) * chartWidth
                val normalized = ((bucket.avgSentiment - minY) / (maxY - minY)).coerceIn(0f, 1f)
                val y = h - normalized * h
                val dotColor = emotionColor(bucket.dominantEmotion)
                val dotRadius = if (selectedGranularity == ChartGranularity.ALL) 4f else 6f
                drawCircle(color = dotColor.copy(alpha = 0.25f), radius = dotRadius + 5f, center = Offset(x, y))
                drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
            }
        }
    }
}

@Composable
fun EmotionBarChart(items: List<CaptureResultEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Emotion Distribution",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (items.isEmpty()) {
            Text(
                "No data yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        val counts = items.groupingBy { it.emotion }.eachCount().toList().sortedByDescending { it.second }
        val max = counts.maxOf { it.second }.toFloat().coerceAtLeast(1f)

        counts.take(6).forEach { (emotion, count) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    emotion.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(modifier = Modifier.weight(1f).height(16.dp)) {
                    val barColor = emotionColor(emotion)
                    Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                        val barWidth = (count / max).coerceIn(0f, 1f) * size.width
                        drawLine(
                            color = barColor.copy(alpha = 0.15f),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = size.height,
                            cap = StrokeCap.Round
                        )
                        if (barWidth > 0f) {
                            drawLine(
                                color = barColor,
                                start = Offset(0f, size.height / 2f),
                                end = Offset(barWidth, size.height / 2f),
                                strokeWidth = size.height,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(28.dp)
                )
            }
        }
    }
}

data class ChartSlice(
    val label: String,
    val value: Int,
    val color: Color
)

fun buildEmotionSlices(items: List<CaptureResultEntity>): List<ChartSlice> {
    val counts = items.groupingBy { it.emotion }.eachCount().toList().sortedByDescending { it.second }
    return counts.map { (label, count) ->
        ChartSlice(label = label, value = count, color = emotionColor(label))
    }
}

fun buildContentSlices(items: List<CaptureResultEntity>): List<ChartSlice> {
    val counts = items.groupingBy { it.contentType }.eachCount().toList().sortedByDescending { it.second }
    return counts.mapIndexed { index, (label, count) ->
        val color = ContentPalette[index % ContentPalette.size]
        ChartSlice(label = label, value = count, color = color)
    }
}

@Composable
fun DonutChart(title: String, slices: List<ChartSlice>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (slices.isEmpty()) {
            Text(
                "No data yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        val total = slices.sumOf { it.value }.coerceAtLeast(1)
        val stroke = 20f
        val emptyArcColor = MaterialTheme.colorScheme.surface

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(140.dp)) {
                drawArc(
                    color = emptyArcColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke)
                )
                var startAngle = -90f
                slices.forEach { slice ->
                    val sweep = (slice.value / total.toFloat()) * 360f
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep.coerceAtLeast(1f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
            }
        }

        slices.take(6).forEach { slice ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(width = 12.dp, height = 8.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = slice.color
                ) {}
                val pct = (slice.value / total.toFloat() * 100).toInt()
                Text(
                    "${slice.label} ($pct%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MiniSparkline(items: List<CaptureResultEntity>, modifier: Modifier = Modifier) {
    if (items.size < 2) return
    val recent = remember(items) {
        items.sortedBy { it.timestampMs }.takeLast(20)
    }
    Canvas(modifier = modifier.fillMaxWidth().height(50.dp)) {
        val w = size.width
        val h = size.height
        val minY = -1f
        val maxY = 1f
        val midColor = Color(0xFF9CA3AF).copy(alpha = 0.1f)
        drawLine(midColor, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)
        val path = Path()
        recent.forEachIndexed { index, item ->
            val x = if (recent.size == 1) 0f else (index / (recent.size - 1f)) * w
            val normalized = ((item.sentiment.toFloat() - minY) / (maxY - minY)).coerceIn(0f, 1f)
            val y = h - normalized * h
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(PrimaryTeal, SecondaryPurple)),
            style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        recent.forEachIndexed { index, item ->
            val x = if (recent.size == 1) 0f else (index / (recent.size - 1f)) * w
            val normalized = ((item.sentiment.toFloat() - minY) / (maxY - minY)).coerceIn(0f, 1f)
            val y = h - normalized * h
            if (index == recent.lastIndex) {
                drawCircle(color = emotionColor(item.emotion), radius = 4f, center = Offset(x, y))
            }
        }
    }
}

@Composable
fun ContentBarChart(items: List<CaptureResultEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Content Distribution",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (items.isEmpty()) {
            Text(
                "No data yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }
        val counts = items.groupingBy { it.contentType }.eachCount().toList().sortedByDescending { it.second }
        val max = counts.maxOf { it.second }.toFloat().coerceAtLeast(1f)
        counts.take(6).forEachIndexed { index, (contentType, count) ->
            val barColor = ContentPalette[index % ContentPalette.size]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    contentType.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(modifier = Modifier.weight(1f).height(16.dp)) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                        val barWidth = (count / max).coerceIn(0f, 1f) * size.width
                        drawLine(
                            color = barColor.copy(alpha = 0.15f),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = size.height,
                            cap = StrokeCap.Round
                        )
                        if (barWidth > 0f) {
                            drawLine(
                                color = barColor,
                                start = Offset(0f, size.height / 2f),
                                end = Offset(barWidth, size.height / 2f),
                                strokeWidth = size.height,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(28.dp)
                )
            }
        }
    }
}

fun emotionColorForChart(emotion: String): Color = emotionColor(emotion)

fun emotionColor(emotion: String): Color {
    return when (emotion.lowercase()) {
        "joy", "happy" -> EmotionJoy
        "sadness", "sad" -> EmotionSadness
        "anger" -> EmotionAnger
        "fear" -> EmotionFear
        "surprise" -> EmotionSurprise
        "disgust" -> EmotionDisgust
        "neutral" -> EmotionNeutral
        else -> Color(0xFF6B7280)
    }
}
