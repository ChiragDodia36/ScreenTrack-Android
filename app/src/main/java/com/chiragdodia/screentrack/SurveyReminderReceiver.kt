package com.chiragdodia.screentrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by the daily AlarmManager alarm — posts the survey reminder notification. */
class SurveyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SurveyReminder.showNow(context)
    }
}
