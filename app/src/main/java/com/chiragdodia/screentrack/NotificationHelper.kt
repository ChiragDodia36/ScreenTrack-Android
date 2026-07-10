package com.chiragdodia.screentrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {
    private const val CHANNEL_ID = "screentrack_wellbeing"
    private const val CHANNEL_NAME = "Wellbeing Alerts"
    // Wellbeing notifications start at 2000 to avoid colliding with the
    // foreground service notification (ID 1001).
    private val notificationIdCounter = AtomicInteger(2000)
    private const val SURVEY_NOTIFICATION_ID = 3000

    fun showWellbeingNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        manager.notify(notificationIdCounter.getAndIncrement(), notification)
    }

    /** Daily survey reminder — tapping opens [formUrl] in the browser. Fixed ID so it replaces, not stacks. */
    fun showSurveyNotification(context: Context, formUrl: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse(formUrl)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Daily check-in")
            .setContentText("Tap to fill out today's short survey.")
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(SURVEY_NOTIFICATION_ID, notification)
    }
}
