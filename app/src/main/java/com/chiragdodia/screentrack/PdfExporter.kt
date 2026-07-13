package com.chiragdodia.screentrack

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - MARGIN * 2

    fun buildSummaryPdf(label: String, items: List<CaptureResultEntity>): ByteArray {
        val document = PdfDocument()

        // ── Page 1: Header + Stats + Emotion bars ──
        val page1 = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create())
        drawPage1(page1.canvas, label, items)
        document.finishPage(page1)

        val page2 = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 2).create())
        drawPage2(page2.canvas, items)
        document.finishPage(page2)

        val output = java.io.ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun drawPage1(canvas: Canvas, label: String, items: List<CaptureResultEntity>): Float {
        var y = MARGIN

        // ── Title ──
        val titlePaint = boldPaint(20f)
        canvas.drawText("ScreenTrack Report", MARGIN, y + 20f, titlePaint)
        y += 30f

        val subPaint = normalPaint(11f, Color.GRAY)
        val dateStr = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Generated: $dateStr", MARGIN, y + 14f, subPaint)
        y += 24f

        // Period chip
        val chipPaint = boldPaint(12f, Color.WHITE)
        val chipBg = Paint().apply { color = Color.parseColor("#00D4AA"); isAntiAlias = true }
        val chipRect = RectF(MARGIN, y, MARGIN + chipPaint.measureText("  $label  ") + 16f, y + 20f)
        canvas.drawRoundRect(chipRect, 10f, 10f, chipBg)
        canvas.drawText("  $label", MARGIN + 4f, y + 14f, chipPaint)
        y += 34f

        divider(canvas, y)
        y += 14f

        // ── Stats block ──
        val stats = SuggestionEngine.summarize(items)
        val labelPaint = normalPaint(11f, Color.parseColor("#9CA3AF"))
        val valuePaint = boldPaint(13f)

        y = statRow(canvas, "Total Captures", items.size.toString(), y, labelPaint, valuePaint)
        y = statRow(canvas, "Avg Sentiment", formatScore(stats.avgSentiment), y, labelPaint, valuePaint)
        y = statRow(canvas, "Dominant Emotion", stats.dominantEmotion.replaceFirstChar { it.uppercase() }, y, labelPaint, valuePaint)
        y = statRow(canvas, "Top Content", stats.topContentType, y, labelPaint, valuePaint)
        y = statRow(canvas, "Negative Ratio", "${(stats.negativeRatio * 100).toInt()}%", y, labelPaint, valuePaint)

        divider(canvas, y)
        y += 16f

        // ── Emotion distribution ──
        val sectionPaint = boldPaint(14f)
        canvas.drawText("Emotion Distribution", MARGIN, y + 14f, sectionPaint)
        y += 26f

        val emotionCounts = items.groupingBy { it.emotion }.eachCount()
            .toList().sortedByDescending { it.second }.take(7)

        if (emotionCounts.isEmpty()) {
            canvas.drawText("No data available.", MARGIN, y + 12f, normalPaint(11f, Color.GRAY))
            y += 20f
        } else {
            val maxCount = emotionCounts.maxOf { it.second }.toFloat().coerceAtLeast(1f)
            val total = items.size.toFloat()
            for ((emotion, count) in emotionCounts) {
                y = drawBar(
                    canvas = canvas,
                    label = emotion.replaceFirstChar { it.uppercase() },
                    count = count,
                    maxCount = maxCount,
                    total = total,
                    barColor = emotionColorInt(emotion),
                    y = y
                )
            }
        }

        return y
    }

    private fun drawPage2(canvas: Canvas, items: List<CaptureResultEntity>) {
        var y = MARGIN + 10f

        val sectionPaint = boldPaint(14f)

        // ── Content distribution ──
        canvas.drawText("Content Distribution", MARGIN, y + 14f, sectionPaint)
        y += 26f

        val contentCounts = items.groupingBy { it.contentType }.eachCount()
            .toList().sortedByDescending { it.second }.take(8)
        val contentPalette = listOf(
            "#00D4AA", "#8B5CF6", "#F59E0B", "#3B82F6", "#EF4444",
            "#10B981", "#F97316", "#6366F1"
        )

        if (contentCounts.isEmpty()) {
            canvas.drawText("No data available.", MARGIN, y + 12f, normalPaint(11f, Color.GRAY))
            y += 20f
        } else {
            val maxCount = contentCounts.maxOf { it.second }.toFloat().coerceAtLeast(1f)
            val total = items.size.toFloat()
            contentCounts.forEachIndexed { index, (ct, count) ->
                y = drawBar(
                    canvas = canvas,
                    label = ct,
                    count = count,
                    maxCount = maxCount,
                    total = total,
                    barColor = Color.parseColor(contentPalette[index % contentPalette.size]),
                    y = y
                )
            }
        }

        divider(canvas, y + 4f)
        y += 22f

        // ── Sentiment trend (simple sparkline) ──
        if (items.size >= 2) {
            canvas.drawText("Sentiment Trend", MARGIN, y + 14f, sectionPaint)
            y += 26f
            drawSparkline(canvas, items, y, 80f)
            y += 96f
        }

        divider(canvas, y + 4f)
        y += 22f

        // ── Suggestion ──
        canvas.drawText("Wellness Suggestion", MARGIN, y + 14f, sectionPaint)
        y += 24f
        val suggestion = SuggestionEngine.suggestionFor(SuggestionEngine.summarize(items))
            ?: "Maintain healthy screen habits with regular breaks."

        val bgPaint = Paint().apply { color = Color.parseColor("#F0FDF9"); isAntiAlias = true }
        val suggRect = RectF(MARGIN, y, PAGE_W - MARGIN, y + 48f)
        canvas.drawRoundRect(suggRect, 8f, 8f, bgPaint)
        val suggPaint = normalPaint(12f, Color.parseColor("#065F46"))
        wrapText(canvas, suggestion, MARGIN + 10f, y + 18f, CONTENT_W - 20f, suggPaint, lineHeight = 16f)
    }

    // ── Draw a horizontal bar row with label, bar, and percentage ──
    private fun drawBar(
        canvas: Canvas,
        label: String,
        count: Int,
        maxCount: Float,
        total: Float,
        barColor: Int,
        y: Float
    ): Float {
        val rowH = 28f
        val labelW = 130f
        val barAreaLeft = MARGIN + labelW + 8f
        val barAreaW = CONTENT_W - labelW - 60f
        val pctW = 48f

        // Label
        val labelPaint = normalPaint(11f)
        val labelTrunc = if (label.length > 18) label.take(16) + "…" else label
        canvas.drawText(labelTrunc, MARGIN, y + rowH * 0.7f, labelPaint)

        // Bar background
        val bgPaint = Paint().apply { color = Color.parseColor("#E5E7EB"); isAntiAlias = true }
        val barH = 12f
        val barTop = y + (rowH - barH) / 2f
        canvas.drawRoundRect(RectF(barAreaLeft, barTop, barAreaLeft + barAreaW, barTop + barH), 6f, 6f, bgPaint)

        // Bar fill
        val fillW = (count / maxCount) * barAreaW
        val fillPaint = Paint().apply { color = barColor; isAntiAlias = true }
        if (fillW > 2f) {
            canvas.drawRoundRect(RectF(barAreaLeft, barTop, barAreaLeft + fillW, barTop + barH), 6f, 6f, fillPaint)
        }

        // Percentage
        val pct = (count / total * 100).toInt()
        val pctPaint = boldPaint(11f, barColor)
        canvas.drawText("$pct%", PAGE_W - MARGIN - pctW, y + rowH * 0.7f, pctPaint)

        return y + rowH + 4f
    }

    // ── Draw sentiment sparkline ──
    private fun drawSparkline(canvas: Canvas, items: List<CaptureResultEntity>, top: Float, height: Float) {
        val sorted = items.sortedBy { it.timestampMs }
        val n = sorted.size
        if (n < 2) return

        val left = MARGIN
        val width = CONTENT_W

        // Zero axis
        val axisPaint = Paint().apply { color = Color.parseColor("#D1D5DB"); strokeWidth = 1f }
        val midY = top + height / 2f
        canvas.drawLine(left, midY, left + width, midY, axisPaint)

        // Labels
        val axisLabelPaint = normalPaint(9f, Color.GRAY)
        canvas.drawText("+1.0", left + width + 4f, top + 10f, axisLabelPaint)
        canvas.drawText("0.0", left + width + 4f, midY + 4f, axisLabelPaint)
        canvas.drawText("-1.0", left + width + 4f, top + height, axisLabelPaint)

        // Line
        val linePaint = Paint().apply {
            color = Color.parseColor("#00D4AA")
            strokeWidth = 2f
            isAntiAlias = true
            style = Paint.Style.STROKE
        }
        val path = android.graphics.Path()
        sorted.forEachIndexed { i, item ->
            val x = left + (i.toFloat() / (n - 1)) * width
            val sentY = top + height - ((item.sentiment.toFloat() + 1f) / 2f) * height
            if (i == 0) path.moveTo(x, sentY) else path.lineTo(x, sentY)
        }
        canvas.drawPath(path, linePaint)
    }

    // ── Stat label + value row ──
    private fun statRow(
        canvas: Canvas,
        label: String,
        value: String,
        y: Float,
        labelPaint: Paint,
        valuePaint: Paint
    ): Float {
        canvas.drawText(label, MARGIN, y + 13f, labelPaint)
        canvas.drawText(value, PAGE_W - MARGIN - valuePaint.measureText(value), y + 13f, valuePaint)
        return y + 20f
    }

    // ── Horizontal divider ──
    private fun divider(canvas: Canvas, y: Float) {
        val p = Paint().apply { color = Color.parseColor("#E5E7EB"); strokeWidth = 1f }
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, p)
    }

    // ── Wrap text to fit within maxWidth ──
    private fun wrapText(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint, lineHeight: Float) {
        val words = text.split(" ")
        var line = ""
        var y = startY
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth) {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
                line = word
            } else {
                line = test
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line, x, y, paint)
    }

    // ── Paint helpers ──
    private fun normalPaint(size: Float, color: Int = Color.BLACK) = Paint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
    }

    private fun boldPaint(size: Float, color: Int = Color.BLACK) = Paint().apply {
        this.color = color
        textSize = size
        isFakeBoldText = true
        isAntiAlias = true
    }

    // ── Emotion colour (Android int) ──
    private fun emotionColorInt(emotion: String): Int = when (emotion.lowercase()) {
        "joy", "happy" -> Color.parseColor("#16A34A")
        "sadness", "sad" -> Color.parseColor("#2563EB")
        "anger" -> Color.parseColor("#DC2626")
        "fear" -> Color.parseColor("#7C3AED")
        "surprise" -> Color.parseColor("#D97706")
        "disgust" -> Color.parseColor("#4B5563")
        "neutral" -> Color.parseColor("#6B7280")
        else -> Color.DKGRAY
    }

    private fun formatScore(score: Double): String = String.format("%.2f", score)
}
