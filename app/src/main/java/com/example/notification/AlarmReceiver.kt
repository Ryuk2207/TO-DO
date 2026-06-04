package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPrefs = context.getSharedPreferences("todo_settings", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPrefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) {
            Log.d("AlarmReceiver", "Notifications are disabled in settings. Skipping alert.")
            return
        }

        val taskId = intent.getIntExtra("task_id", 0)
        val taskTitle = intent.getStringExtra("task_title") ?: "Scheduled Task"
        val taskTime = intent.getStringExtra("task_time") ?: "Now"

        Log.d("AlarmReceiver", "Received alarm trigger for task $taskId: $taskTitle at $taskTime")
        NotificationHelper.showTaskNotification(context, taskId, taskTitle, taskTime)
    }
}
