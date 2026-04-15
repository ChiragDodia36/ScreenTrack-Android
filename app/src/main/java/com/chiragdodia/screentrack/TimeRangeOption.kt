package com.chiragdodia.screentrack

import java.util.concurrent.TimeUnit

enum class TimeRangeOption(val label: String, val hours: Int) {
    LAST_HOUR("1 hour", 1),
    LAST_6_HOURS("6 hours", 6),
    LAST_24_HOURS("24 hours", 24),
    LAST_7_DAYS("7 days", 168),
    LAST_30_DAYS("30 days", 720);

    fun sinceMs(): Long {
        return System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours.toLong())
    }
}
