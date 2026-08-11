package com.chiragdodia.screentrack

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Pos = Color(0xFF00D4AA)
private val Neg = Color(0xFFFF6B6B)
private val Neu = Color(0xFF5B6472)
private val Ink = Color(0xFFE8ECF4)
private val Muted = Color(0xFF9CA3AF)
private val Hair = Color(0xFF2A2F3A)
private val EmptyTick = Color(0xFF3A3F4B)
private val SegBg = Color(0xFF0E1017)
private val SegSel = Color(0xFF252A36)

/**
 * Diverging hourly/daily mood bar chart. Bars rise above the zero line for
 * net-positive hours, drop below for negative; empty buckets show a flat tick,
 * lightly-used ones fade. Tap a bar to see its detail. Day / Week / Month tabs.
 */
@Composable
fun MoodBarChart(items: List<CaptureResultEntity>, modifier: Modifier = Modifier) {
    var range by remember { mutableStateOf(MoodRange.DAY) }
    var selected by remember { mutableStateOf<Int?>(null) }

    val intervalSec = remember(items) { MoodBuckets.estimateIntervalSeconds(items) }
    val buckets = remember(items, range) {
        MoodBuckets.build(items, range, System.currentTimeMillis(), intervalSec)
    }
    val period = when (range) { MoodRange.DAY -> "Today"; MoodRange.WEEK -> "This week"; MoodRange.MONTH -> "This month" }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Mood through the day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(period, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Spacer(Modifier.height(10.dp))
        SegmentedControl(range) { range = it; selected = null }
        Spacer(Modifier.height(8.dp))
        DetailLine(buckets.getOrNull(selected ?: -1), range)
        Spacer(Modifier.height(6.dp))

        var widthPx by remember { mutableStateOf(1) }
        val labelPaint = remember {
            Paint().apply { color = Muted.toArgb(); textAlign = Paint.Align.CENTER; isAntiAlias = true }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(buckets, widthPx) {
                    detectTapGestures { off ->
                        if (buckets.isNotEmpty() && widthPx > 0) {
                            val i = (off.x / (widthPx.toFloat() / buckets.size)).toInt().coerceIn(0, buckets.size - 1)
                            selected = if (selected == i) null else i
                        }
                    }
                }
        ) {
            val n = buckets.size
            if (n == 0) return@Canvas
            val labelH = 16.dp.toPx()
            val plotTop = 6.dp.toPx()
            val plotBottom = size.height - labelH
            val zeroY = (plotTop + plotBottom) / 2f
            val halfH = (plotBottom - plotTop) / 2f
            val slot = size.width / n
            val gap = min(slot * 0.3f, if (range == MoodRange.MONTH) 2.dp.toPx() else 5.dp.toPx())
            val bw = slot - gap
            val r = CornerRadius(min(3.dp.toPx(), bw / 2f))
            labelPaint.textSize = 9.5.dp.toPx()

            // faint half-scale gridlines
            listOf(0.5f, -0.5f).forEach { g ->
                val y = zeroY - g * halfH
                drawLine(Hair, Offset(0f, y), Offset(size.width, y), 1f)
            }

            buckets.forEachIndexed { i, b ->
                val x = i * slot + gap / 2f
                if (b.isEmpty) {
                    drawRoundRect(EmptyTick, Offset(x, zeroY - 1.5f), Size(bw, 3f), CornerRadius(1.5f), alpha = 0.7f)
                } else {
                    val col = if (abs(b.netSentiment) < 0.08) Neu else if (b.netSentiment >= 0) Pos else Neg
                    val h = max((abs(b.netSentiment) * halfH).toFloat(), 2f)
                    val top = if (b.netSentiment >= 0) zeroY - h else zeroY
                    val activeFrac = (b.activeMinutes.toFloat() / b.fullMinutes).coerceIn(0f, 1f)
                    val alpha = if (selected == i) 1f else 0.4f + 0.55f * activeFrac
                    drawRoundRect(col, Offset(x, top), Size(bw, h), r, alpha = alpha)
                    if (selected == i) {
                        drawRoundRect(Ink, Offset(x, top), Size(bw, h), r, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
                if (b.axisLabel.isNotEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(b.axisLabel, x + bw / 2f, size.height - 3.dp.toPx(), labelPaint)
                }
            }
            drawLine(Ink, Offset(0f, zeroY), Offset(size.width, zeroY), 1.6.dp.toPx(), alpha = 0.85f)
        }

        Spacer(Modifier.height(10.dp))
        Legend()
    }
}

@Composable
private fun SegmentedControl(selected: MoodRange, onSelect: (MoodRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SegBg).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MoodRange.values().forEach { r ->
            val on = r == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                    .background(if (on) SegSel else Color.Transparent)
                    .clickable { onSelect(r) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(r.label, style = MaterialTheme.typography.labelMedium, color = if (on) Ink else Muted, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailLine(sel: MoodBucket?, range: MoodRange) {
    val text = when {
        sel == null -> "Tap a bar for details"
        sel.isEmpty -> "${sel.title} · No activity"
        else -> {
            val s = (if (sel.netSentiment >= 0) "+" else "") + String.format("%.2f", sel.netSentiment)
            "${sel.title} · $s · ${sel.dominantEmotion.replaceFirstChar { it.uppercase() }} · ${activeText(sel.activeMinutes, range)}"
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (sel == null) Muted else Ink)
}

private fun activeText(min: Int, range: MoodRange): String =
    if (range == MoodRange.DAY || min < 60) "$min min active"
    else "${min / 60}h ${min % 60}m active"

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem(Pos, "Positive"); LegendItem(Neg, "Negative"); LegendItem(Neu, "Neutral"); LegendItem(EmptyTick, "No activity")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.height(10.dp).width(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}
