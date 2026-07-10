package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Calendar
import org.junit.Test

class SurveyReminderTest {

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    @Test
    fun triggerIsInFutureWithin24h() {
        val now = System.currentTimeMillis()
        val next = SurveyReminder.nextTriggerAt(now, 20)
        assertTrue("must be in the future", next > now)
        assertTrue("must be within 24h", next - now <= 24 * 60 * 60 * 1000L)
    }

    @Test
    fun triggerLandsOnRequestedHour() {
        val now = System.currentTimeMillis()
        assertEquals(20, hourOf(SurveyReminder.nextTriggerAt(now, 20)))
        assertEquals(8, hourOf(SurveyReminder.nextTriggerAt(now, 8)))
    }
}
