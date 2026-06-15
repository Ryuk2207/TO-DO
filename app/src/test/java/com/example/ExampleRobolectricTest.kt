package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TaskRepository
import com.example.data.Task
import com.example.data.DateHelper
import com.example.ui.TaskViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun testViewModelFlowWithImportedData() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.taskDao()
    val repository = TaskRepository(dao)

    val jsonString = """
    {
        "settings": {
            "wallpaper_enabled": true,
            "wallpaper_style": "custom_photo",
            "custom_wallpaper_url": "/data/user/0/com.aistudio.todoapp.lyvpxn/files/custom_wallpaper.mp4",
            "vibrations_enabled": false,
            "sounds_enabled": false,
            "notifications_enabled": true,
            "streak_one_time_increment_applied_v3": true,
            "slot_categories": "[\"Slot 1: 8 AM to 12 PM -- 2 Classes\",\"Slot 2: 12 PM to 1 PM -- Break\",\"Slot 3:  1 PM to 5 PM -- 2 Classes\",\"Slot 4: 5 PM to 8 PM -- Classes DPP\\/H.W\",\"Slot 5:  8 PM to 11 PM -- Last chapter revision\\/practice\",\"Custom Tasks\"]"
        },
        "streak": {
            "streakCount": 0,
            "lastCompletedDateString": "2026-06-07"
        },
        "tasks": [
            {
                "title": "Physics Question practice",
                "hour": 7,
                "minute": 0,
                "isCompleted": false,
                "dateString": "2026-06-15",
                "slotCategory": "Slot 1: 8 AM to 12 PM -- 2 Classes",
                "timeRange": "07:00"
            },
            {
                "title": "Physical chemistry lecture",
                "hour": 9,
                "minute": 0,
                "isCompleted": false,
                "dateString": "2026-06-15",
                "slotCategory": "Slot 1: 8 AM to 12 PM -- 2 Classes",
                "timeRange": "09:00"
            }
        ]
    }
    """.trimIndent()

    // 1. Setup slot categories in prefs directly to match import settings
    val sharedPrefs = context.getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE)
    sharedPrefs.edit().putString("slot_categories", "[\"Slot 1: 8 AM to 12 PM -- 2 Classes\",\"Slot 2: 12 PM to 1 PM -- Break\",\"Slot 3:  1 PM to 5 PM -- 2 Classes\",\"Slot 4: 5 PM to 8 PM -- Classes DPP\\/H.W\",\"Slot 5:  8 PM to 11 PM -- Last chapter revision\\/practice\",\"Custom Tasks\"]").commit()

    // 2. Load the tasks directly into database to see if repository queries them correctly
    dao.clearAllTasks()
    dao.insertTask(Task(
        id = 1,
        title = "Physics Question practice",
        hour = 7,
        minute = 0,
        isCompleted = false,
        dateString = "2026-06-15",
        slotCategory = "Slot 1: 8 AM to 12 PM -- 2 Classes",
        timeRange = "07:00"
    ))
    dao.insertTask(Task(
        id = 2,
        title = "Physical chemistry lecture",
        hour = 9,
        minute = 0,
        isCompleted = false,
        dateString = "2026-06-15",
        slotCategory = "Slot 1: 8 AM to 12 PM -- 2 Classes",
        timeRange = "09:00"
    ))

    val viewModel = TaskViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)
    viewModel.loadSlotCategories()

    val dbTasks = dao.getTasksForDateList("2026-06-15")
    println("LOG: Loaded DB tasks count: ${dbTasks.size}")
    dbTasks.forEach {
        println("LOG: Loaded DB Task: ${it.title}, dateString: ${it.dateString}, slotCategory: ${it.slotCategory}")
    }

    // Load actual slot categories from viewmodel state
    val slots = viewModel.slotCategories.value
    println("LOG: Slot categories count: ${slots.size}")
    slots.forEach {
        println("LOG: Loaded slot category: $it")
    }

    // Call repository getTasksForDateList with custom slots loaded
    val prepopulatedTasks = repository.getTasksForDateList("2026-06-15", slots)
    println("LOG: Prepopulated tasks list count: ${prepopulatedTasks.size}")
    prepopulatedTasks.forEach {
        println("LOG: Prepopulated item: ${it.title}, slotCategory: ${it.slotCategory}")
    }
  }
}
