package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.data.AppDatabase
import com.example.data.TaskRepository
import com.example.notification.AlarmScheduler
import com.example.ui.TodoAppScreen
import com.example.ui.TaskViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: TaskViewModel
    private var dateChangeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge support for modern overlay bounds
        enableEdgeToEdge()

        // Schedule the daily 2pm and 8pm checks
        AlarmScheduler.scheduleDailyChecks(applicationContext)

        // Local SQLite Room Database & Repository initialization
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TaskRepository(database.taskDao())

        // Injects application context & repository dependency cleanly via factory
        val vm: TaskViewModel by viewModels {
            TaskViewModel.Factory(application, repository)
        }
        viewModel = vm

        // Register broadcast receiver to handle date changes/12 midnight refreshes immediately
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        dateChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.refreshToTodayIfNeeded()
            }
        }
        try {
            registerReceiver(dateChangeReceiver, filter)
        } catch (e: Exception) {
            // Fallback for strict OS environments or execution sandboxes
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    TodoAppScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshToTodayIfNeeded()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dateChangeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore if receiver not registered or already cleaned up
            }
        }
    }
}
