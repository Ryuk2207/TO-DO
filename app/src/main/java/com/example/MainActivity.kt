package com.example

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
import com.example.ui.TodoAppScreen
import com.example.ui.TaskViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge support for modern overlay bounds
        enableEdgeToEdge()

        // Local SQLite Room Database & Repository initialization
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TaskRepository(database.taskDao())

        // Injects application context & repository dependency cleanly via factory
        val viewModel: TaskViewModel by viewModels {
            TaskViewModel.Factory(application, repository)
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
}
