package com.chiragdodia.screentrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Daily survey reminder. Fires a notification once a day that opens the study
 * Google Form in the browser. No network permission needed — the browser
 * handles the request, so the app itself stays sealed.
 */
object SurveyReminder {

    // ponytail: demo form on Firebase Hosting. Swap for Krista's Google Form once it exists.
    const val FORM_URL = "https://screentrack-study-2026.web.app"

    private const val REMINDER_HOUR = 20 // 8 PM local
    private const val REQUEST_CODE = 3001

    /** Next occurrence of [hour]:00 at or after [now]. Pure — unit-tested. */
    fun nextTriggerAt(now: Long, hour: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Schedule the daily reminder. Inexact = no special alarm permission. */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(
            AlarmManager.RTC,
            nextTriggerAt(System.currentTimeMillis(), REMINDER_HOUR),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context)
        )
    }

    /** Post the reminder notification now (used by the alarm and the test button). */
    fun showNow(context: Context) {
        NotificationHelper.showSurveyNotification(context, FORM_URL)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SurveyReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
