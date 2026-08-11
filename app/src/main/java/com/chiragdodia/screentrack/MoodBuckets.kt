package com.chiragdodia.screentrack

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class MoodRange(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month") }

/** One bar in the mood chart. netSentiment drives up/down; activeMinutes drives fade. */
data class MoodBucket(
    val axisLabel: String,     // short x-axis label ("" = hide)
    val title: String,         // tooltip title, e.g. "8am–9am", "Mon", "Aug 5"
    val netSentiment: Double,  // −1..+1 average; 0.0 when empty
    val activeMinutes: Int,    // estimated active minutes in this bucket
    val fullMinutes: Int,      // bucket capacity (60 for an hour, 1440 for a day) — for fade
    val dominantEmotion: String, // top emotion label, "" when empty
    val isEmpty: Boolean,      // no captures in this bucket
)

/**
 * Pure bucketing of captures into Day (24 hourly) / Week (7 daily) / Month
 * (30 daily) bars. Every bucket in the window is emitted — empty ones flagged,
 * never dropped. No Android UI imports — unit-tested on the JVM.
 */
object MoodBuckets {

    /**
     * Infer the sampling interval from the median gap between consecutive
     * captures (robust to session pauses). Defaults to 15s. Shared by the chart
     * and the "active duration" figure so both agree.
     */
    fun estimateIntervalSeconds(items: List<CaptureResultEntity>): Double {
        if (items.size < 2) return 15.0
        val gaps = items.map { it.timestampMs }.sorted()
            .zipWithNext { a, b -> (b - a) / 1000.0 }
            .filter { it in 1.0..120.0 }
        if (gaps.isEmpty()) return 15.0
        val sorted = gaps.sorted()
        return sorted[sorted.size / 2]
    }

    fun build(items: List<CaptureResultEntity>, range: MoodRange, now: Long, intervalSec: Double): List<MoodBucket> =
        when (range) {
            MoodRange.DAY -> buildHourly(items, now, intervalSec)
            MoodRange.WEEK -> buildDaily(items, now, days = 7, intervalSec, monthStyle = false)
            MoodRange.MONTH -> buildDaily(items, now, days = 30, intervalSec, monthStyle = true)
        }

    private fun buildHourly(items: List<CaptureResultEntity>, now: Long, intervalSec: Double): List<MoodBucket> {
        val startOfDay = floorToDay(now)
        return (0 until 24).map { h ->
            val from = startOfDay + h * HOUR_MS
            val inHour = items.filter { it.timestampMs in from until (from + HOUR_MS) }
            bucketOf(
                inHour, intervalSec, fullMinutes = 60,
                axisLabel = if (h % 3 == 0) hourShort(h) else "",
                title = "${hour12(h)}–${hour12((h + 1) % 24)}",
            )
        }
    }

    private fun buildDaily(items: List<CaptureResultEntity>, now: Long, days: Int, intervalSec: Double, monthStyle: Boolean): List<MoodBucket> {
        val today = floorToDay(now)
        val dow = SimpleDateFormat("EEE", Locale.US)
        val md = SimpleDateFormat("MMM d", Locale.US)
        return (days - 1 downTo 0).map { back ->
            val from = today - back * DAY_MS
            val inDay = items.filter { it.timestampMs in from until (from + DAY_MS) }
            val cal = Calendar.getInstance().apply { timeInMillis = from }
            val idxFromEnd = days - 1 - back
            bucketOf(
                inDay, intervalSec, fullMinutes = 8 * 60, // 8h active = a "full" day for fade
                axisLabel = if (monthStyle) { if (idxFromEnd % 7 == 0) cal.get(Calendar.DAY_OF_MONTH).toString() else "" }
                            else dow.format(from).take(1),
                title = if (monthStyle) md.format(from) else dow.format(from),
            )
        }
    }

    private fun bucketOf(inBucket: List<CaptureResultEntity>, intervalSec: Double, fullMinutes: Int, axisLabel: String, title: String): MoodBucket {
        if (inBucket.isEmpty()) {
            return MoodBucket(axisLabel, title, 0.0, 0, fullMinutes, "", isEmpty = true)
        }
        val net = inBucket.map { it.sentiment }.average()
        val active = (inBucket.size * intervalSec / 60.0).toInt().coerceIn(1, fullMinutes)
        val dominant = inBucket.groupingBy { it.emotion }.eachCount().maxByOrNull { it.value }?.key ?: ""
        return MoodBucket(axisLabel, title, net, active, fullMinutes, dominant, isEmpty = false)
    }

    private fun floorToDay(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun hour12(h: Int): String = "${if (h % 12 == 0) 12 else h % 12}${if (h < 12) "am" else "pm"}"
    private fun hourShort(h: Int): String = "${if (h % 12 == 0) 12 else h % 12}${if (h < 12) "a" else "p"}"

    private const val HOUR_MS = 60 * 60 * 1000L
    private const val DAY_MS = 24 * HOUR_MS
}
