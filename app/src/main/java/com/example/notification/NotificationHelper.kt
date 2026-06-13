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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class InAppNotification(
    val title: String,
    val text: String,
    val time: String
)

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

        // Also log inside client in-app notifications drawer
        val displayTime = if (timeString.matches(Regex("\\d{2}:\\d{2}"))) {
            try {
                val parts = timeString.split(":")
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                val ampm = if (h >= 12) "PM" else "AM"
                val h12 = if (h % 12 == 0) 12 else h % 12
                String.format("%02d:%02d %s", h12, m, ampm)
            } catch (e: Exception) {
                timeString
            }
        } else {
            timeString
        }
        addInAppNotification(safeContext, "Task: \"$taskTitle\"", "It's time for your task: \"$taskTitle\". Take dynamic action!", displayTime)

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

    fun showCheckAlertNotification(context: Context, title: String, message: String, notificationId: Int) {
        val safeContext = context.safeAttribution()
        createNotificationChannel(safeContext)

        val intent = Intent(safeContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            safeContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(safeContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = safeContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Degrade gracefully
        }
    }

    fun addInAppNotification(context: Context, title: String, text: String, time: String) {
        val safeContext = context.safeAttribution()
        val sharedPrefs = safeContext.getSharedPreferences("in_app_notifications_pref", Context.MODE_PRIVATE)
        val notificationsRaw = sharedPrefs.getString("list_v1", "") ?: ""
        
        // Simple serialization (e.g. title|text|time$$$title|text|time)
        val newItem = "${title.replace("|", "_").replace("$$$", "_")}|${text.replace("|", "_").replace("$$$", "_")}|$time"
        val updated = if (notificationsRaw.isEmpty()) {
            newItem
        } else {
            "$newItem$$$notificationsRaw" // newest first
        }
        
        sharedPrefs.edit().putString("list_v1", updated).apply()

        // Persistent Room database insertion running off-thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.example.data.AppDatabase.getDatabase(safeContext)
                db.taskDao().insertNotification(
                    com.example.data.InAppNotificationEntity(
                        title = title,
                        text = text,
                        time = time
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getInAppNotifications(context: Context): List<InAppNotification> {
        val safeContext = context.safeAttribution()
        val sharedPrefs = safeContext.getSharedPreferences("in_app_notifications_pref", Context.MODE_PRIVATE)
        val notificationsRaw = sharedPrefs.getString("list_v1", "") ?: ""
        if (notificationsRaw.isEmpty()) return emptyList()
        
        val demoTitles = setOf(
            "🔔 Status: Habit Tracker Verified",
            "🔥 Streak Amplified!",
            "⏰ System Check",
            "💡 Daily Focus Recommendation",
            "🎯 Morning Core Run Complete",
            "📈 Weekly Productivity Audit",
            "🔑 Device Sync Completed",
            "🌟 Glass Theme Unlocked",
            "🚀 Launch Kickstart Habit",
            "🛡️ Offline Database Repaired"
        )

        return notificationsRaw.split("$$$").mapNotNull {
            val parts = it.split("|")
            if (parts.size >= 3) {
                InAppNotification(parts[0], parts[1], parts[2])
            } else {
                null
            }
        }.filter { !demoTitles.contains(it.title) }
    }

    fun clearInAppNotifications(context: Context) {
        val safeContext = context.safeAttribution()
        val sharedPrefs = safeContext.getSharedPreferences("in_app_notifications_pref", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("list_v1").apply()

        // Persistent Room database clear running off-thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.example.data.AppDatabase.getDatabase(safeContext)
                db.taskDao().clearAllNotifications()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeInAppNotificationAtIndex(context: Context, index: Int) {
        val safeContext = context.safeAttribution()
        val sharedPrefs = safeContext.getSharedPreferences("in_app_notifications_pref", Context.MODE_PRIVATE)
        val notificationsRaw = sharedPrefs.getString("list_v1", "") ?: ""
        if (notificationsRaw.isEmpty()) return
        
        val items = notificationsRaw.split("$$$").toMutableList()
        if (index in items.indices) {
            items.removeAt(index)
            val updated = items.joinToString("$$$")
            if (updated.isEmpty()) {
                sharedPrefs.edit().remove("list_v1").apply()
            } else {
                sharedPrefs.edit().putString("list_v1", updated).apply()
            }
        }
    }

    var hasSeededDemo = false

    fun seedDemoNotifications(context: Context) {
        // Disabled as requested: demo notifications are removed; notifications will only appear on real notifications and alerts
    }
}
