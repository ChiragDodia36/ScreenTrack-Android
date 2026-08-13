package com.chiragdodia.screentrack

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Hourly CSV report for the study. One row per hour that has captures:
 *   Date, Hour, <7 emotion columns>, positive_pct, negative_pct, neutral_pct, <content columns>
 * - Emotion columns (6 Ekman basics + neutral) = average model score (0..1) over the hour.
 * - Sentiment + content columns = percentage (0..100) of the hour in each bucket.
 * Pure — no Android imports, unit-tested on the JVM.
 */
object CsvExporter {

    val HEADER: List<String> =
        listOf("Date", "Hour") + EkmanMap.EKMAN_EMOTIONS +
            listOf("positive_pct", "negative_pct", "neutral_pct") +
            ContentClassifier.CATEGORY_NAMES

    fun buildHourlyCsv(items: List<CaptureResultEntity>): String {
        val sb = StringBuilder(HEADER.joinToString(",") { cell(it) }).append('\n')
        val keyFmt = SimpleDateFormat("yyyyMMddHH", Locale.US)
        items.groupBy { keyFmt.format(it.timestampMs) }
            .toSortedMap()
            .forEach { (_, rows) -> sb.append(rowFor(rows)).append('\n') }
        return sb.toString()
    }

    private fun rowFor(rows: List<CaptureResultEntity>): String {
        val cal = Calendar.getInstance().apply { timeInMillis = rows.first().timestampMs }
        val n = rows.size
        val decoded = rows.map { EmotionScoresJson.decode(it.emotionScoresJson) }

        val cells = ArrayList<String>(HEADER.size)
        cells += cell(formatDate(cal))
        cells += cell(formatHour(cal))
        // emotion columns (6 Ekman + neutral): average score over the hour
        EkmanMap.EKMAN_EMOTIONS.forEach { e ->
            val avg = decoded.sumOf { it[e] ?: 0.0 } / n
            cells += String.format(Locale.US, "%.3f", avg)
        }
        // sentiment buckets as % of the hour
        val pos = rows.count { it.sentiment >= 0.2 }
        val neg = rows.count { it.sentiment <= -0.2 }
        val neu = n - pos - neg
        cells += pct(pos, n); cells += pct(neg, n); cells += pct(neu, n)
        // content categories as % of the hour
        ContentClassifier.CATEGORY_NAMES.forEach { c ->
            cells += pct(rows.count { it.contentType == c }, n)
        }
        return cells.joinToString(",")
    }

    private fun pct(count: Int, total: Int): String =
        if (total == 0) "0" else Math.round(100.0 * count / total).toString()

    private fun formatDate(cal: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd (EEE)", Locale.US).format(cal.time)

    private fun formatHour(cal: Calendar): String {
        val h = cal.get(Calendar.HOUR_OF_DAY)
        return String.format(Locale.US, "%02d:00-%02d:00", h, (h + 1) % 24)
    }

    /** RFC-4180 quoting for any cell containing a comma/quote/newline. */
    private fun cell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' })
            "\"" + value.replace("\"", "\"\"") + "\""
        else value
}
