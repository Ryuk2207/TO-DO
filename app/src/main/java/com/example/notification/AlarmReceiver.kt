package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.DateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPrefs = context.getSharedPreferences("todo_settings", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPrefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) {
            Log.d("AlarmReceiver", "Notifications are disabled in settings. Skipping alert.")
            return
        }

        val dailyCheckType = intent.getStringExtra("daily_check_type")
        if (dailyCheckType != null) {
            handleDailyCheck(context, dailyCheckType)
            return
        }

        val taskId = intent.getIntExtra("task_id", 0)
        val taskTitle = intent.getStringExtra("task_title") ?: "Scheduled Task"
        val taskTime = intent.getStringExtra("task_time") ?: "Now"

        Log.d("AlarmReceiver", "Received alarm trigger for task $taskId: $taskTitle at $taskTime")
        NotificationHelper.showTaskNotification(context, taskId, taskTitle, taskTime)
    }

    private fun handleDailyCheck(context: Context, checkType: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val taskDao = db.taskDao()
                val todayString = DateHelper.getTodayString()
                val tasks = taskDao.getTasksForDateList(todayString)

                if (checkType == "alert_2pm") {
                    // Check if 0 tasks are completed (no task ticked)
                    val anyCompleted = tasks.any { it.isCompleted }
                    if (!anyCompleted && tasks.isNotEmpty()) {
                        NotificationHelper.showCheckAlertNotification(
                            context = context,
                            title = "Incomplete Today",
                            message = "It is 2:00 PM and you haven't completed any tasks today. Let's make progress!",
                            notificationId = 10002
                        )
                        NotificationHelper.addInAppNotification(
                            context = context,
                            title = "No Tasks Ticked (2 PM)",
                            text = "It is 2:00 PM and you haven't ticked off any tasks yet today. Tap to check your schedule!",
                            time = "02:00 PM"
                        )
                    }
                } else if (checkType == "alert_8pm") {
                    // Check if any task is not complete
                    val anyIncomplete = tasks.any { !it.isCompleted }
                    if (anyIncomplete && tasks.isNotEmpty()) {
                        NotificationHelper.showCheckAlertNotification(
                            context = context,
                            title = "Secure Your Day",
                            message = "You have unfinished tasks. Secure them before the end of the day!",
                            notificationId = 10008
                        )
                        NotificationHelper.addInAppNotification(
                            context = context,
                            title = "Secure Your Day (8 PM)",
                            text = "Secure your day and streak! You have unfinished tasks remaining. Complete them before midnight!",
                            time = "08:00 PM"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to run daily alarms checks", e)
            } finally {
                pendingResult.finish()
                // Re-schedule next check
                AlarmScheduler.scheduleDailyChecks(context)
            }
        }
    }
}
