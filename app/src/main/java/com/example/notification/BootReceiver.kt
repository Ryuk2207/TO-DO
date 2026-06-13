package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.DateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getDatabase(context)
                    val today = DateHelper.getTodayString()
                    // Re-schedule all uncompleted tasks for today
                    val tasks = database.taskDao().getTasksForDateList(today)
                    for (task in tasks) {
                        if (!task.isCompleted) {
                            AlarmScheduler.scheduleAlarm(context, task)
                        }
                    }
                    AlarmScheduler.scheduleDailyChecks(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
