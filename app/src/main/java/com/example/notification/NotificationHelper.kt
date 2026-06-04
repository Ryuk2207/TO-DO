package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.ui.safeAttribution

object NotificationHelper {
    private const val CHANNEL_ID = "todo_alerts_channel_id"
    private const val CHANNEL_NAME = "To-Do List Reminders"
    private const val CHANNEL_DESC = "Notifications to alert you about your scheduled tasks"

    fun createNotificationChannel(context: Context) {
        val safeContext = context.safeAttribution()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = safeContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTaskNotification(context: Context, taskId: Int, taskTitle: String, timeString: String) {
        val safeContext = context.safeAttribution()
        createNotificationChannel(safeContext)

        val intent = Intent(safeContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            safeContext,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(safeContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // System default alarm icon (safe and robust)
            .setContentTitle("To-Do Reminder")
            .setContentText("It is $timeString! Time for: \"$taskTitle\"")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = safeContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(taskId, notification)
        } catch (e: SecurityException) {
            // Permission not granted, degrade gracefully
        }
    }
}
