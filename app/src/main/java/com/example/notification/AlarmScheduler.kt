package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.Task
import com.example.ui.safeAttribution
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    fun scheduleAlarm(context: Context, task: Task) {
        val safeContext = context.safeAttribution()
        val alarmManager = safeContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(safeContext, AlarmReceiver::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("task_title", task.title)
            putExtra("task_time", String.format(Locale.getDefault(), "%02d:%02d", task.hour, task.minute))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            safeContext,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Parse date and time
        val calendar = Calendar.getInstance()
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(task.dateString)
            if (date != null) {
                calendar.time = date
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse date for task ${task.id}", e)
        }

        calendar.set(Calendar.HOUR_OF_DAY, task.hour)
        calendar.set(Calendar.MINUTE, task.minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // If the calculated time is in the past, don't trigger (unless they want to hear it right away if they scheduled 1 minute ago)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            // If scheduled for today and past, scheduling for today's past doesn't alarm. We'll skip it to avoid immediate ringing.
            Log.d(TAG, "Scheduled time is in the past, skipping alarm setup.")
            return
        }

        // Use exact alarm if possible, fall back to normal alarm if exact alarm permission is missing on API 31+
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Successfully scheduled alarm for task ${task.id} at ${calendar.time}")
        } catch (e: Exception) {
            // Security or other exception fallback
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "Fallback scheduled alarm completed for task ${task.id}")
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule alarm", ex)
            }
        }
    }

    fun cancelAlarm(context: Context, task: Task) {
        val safeContext = context.safeAttribution()
        val alarmManager = safeContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(safeContext, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            safeContext,
            task.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Successfully canceled alarm for task ${task.id}")
        }
    }

    fun scheduleDailyChecks(context: Context) {
        val safeContext = context.safeAttribution()
        val alarmManager = safeContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // 1. 2 PM Alarm (14:00)
        scheduleDailyAlarmAt(safeContext, alarmManager, 14, 0, 9922, "alert_2pm")

        // 2. 8 PM Alarm (20:00)
        scheduleDailyAlarmAt(safeContext, alarmManager, 20, 0, 9988, "alert_8pm")
    }

    private fun scheduleDailyAlarmAt(
        context: Context,
        alarmManager: AlarmManager,
        hour: Int,
        minute: Int,
        requestCode: Int,
        actionExtra: String
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("daily_check_type", actionExtra) // "alert_2pm" or "alert_8pm"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If time has already passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled daily $actionExtra check at ${calendar.time} with request code $requestCode")
        } catch (e: Exception) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule daily check $actionExtra", ex)
            }
        }
    }
}
