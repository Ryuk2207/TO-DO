package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.notification.AlarmScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TaskViewModel(
    private val application: Application,
    private val repository: TaskRepository
) : AndroidViewModel(application) {

    // Current app reference date (supports simulation)
    private val _currentDate = MutableStateFlow(DateHelper.getTodayString())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    // Tasks for the currently active date reference
    val tasks: StateFlow<List<Task>> = _currentDate
        .flatMapLatest { date ->
            flow {
                val list = repository.checkAndPrepopulateTasksForDate(date)
                list.forEach { task ->
                    if (!task.isCompleted) {
                        AlarmScheduler.scheduleAlarm(application, task)
                    }
                }
                TodoWidgetProvider.triggerUpdate(application)
                emitAll(repository.getTasksForDate(date))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current Streak Record
    val streak: StateFlow<Streak?> = repository.getStreak()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Operation messages/alerts (Toasts/Snackbars)
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        // Initial check for streak break when launched
        viewModelScope.launch {
            repository.checkAndResetStreak(_currentDate.value)
        }
    }

    fun addTask(title: String, hour: Int, minute: Int) {
        if (title.isBlank()) {
            sendEvent(UiEvent.ShowToast("Task title cannot be empty"))
            return
        }
        viewModelScope.launch {
            val date = _currentDate.value
            val newTask = Task(
                title = title,
                hour = hour,
                minute = minute,
                isCompleted = false,
                dateString = date,
                slotCategory = "Custom Tasks",
                timeRange = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
            )
            repository.insertTask(newTask)
            
            // Query full task with newly assigned ID from database to schedule alarm properly
            val updatedTasks = repository.getTasksForDateList(date)
            // Retrieve latest added task with matching attributes
            val insertedTask = updatedTasks.find { 
                it.title == title && it.hour == hour && it.minute == minute && !it.isCompleted 
            }
            if (insertedTask != null) {
                AlarmScheduler.scheduleAlarm(application, insertedTask)
            }
            TodoWidgetProvider.triggerUpdate(application)
            sendEvent(UiEvent.ShowToast("Task added and alarm scheduled!"))
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = !task.isCompleted)
            repository.updateTask(updated)
            
            if (updated.isCompleted) {
                // Cancel scheduled notification since task is done
                AlarmScheduler.cancelAlarm(application, updated)
            } else {
                // Reschedule notification
                AlarmScheduler.scheduleAlarm(application, updated)
            }
            TodoWidgetProvider.triggerUpdate(application)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            AlarmScheduler.cancelAlarm(application, task)
            repository.deleteTask(task)
            TodoWidgetProvider.triggerUpdate(application)
            sendEvent(UiEvent.ShowToast("Task removed"))
        }
    }

    fun saveCurrentDay() {
        viewModelScope.launch {
            val date = _currentDate.value
            when (val result = repository.saveCurrentDayTasks(date)) {
                is SaveResult.Success -> {
                    TodoWidgetProvider.triggerUpdate(application)
                    sendEvent(UiEvent.SaveSuccess(result.newStreak))
                }
                is SaveResult.AlreadyDone -> {
                    sendEvent(UiEvent.ShowToast(result.message))
                }
                is SaveResult.Error -> {
                    sendEvent(UiEvent.ShowToast(result.message))
                }
            }
        }
    }

    /**
     * Simulation: advances reference calendar date by 1 day
     */
    fun simulateNextDay() {
        viewModelScope.launch {
            val nextDay = DateHelper.getTomorrowString(_currentDate.value)
            _currentDate.value = nextDay
            
            // Perform active check on the new day. 
            // If the user left task lists unsaved on previous days, the streak is broken!
            repository.checkAndResetStreak(nextDay)
            sendEvent(UiEvent.ShowToast("Advanced simulator to: ${DateHelper.getFormattedDisplayDate(nextDay)}"))
        }
    }

    /**
     * Simulation: resets reference calendar date to natural system date
     */
    fun resetSimulationToToday() {
        viewModelScope.launch {
            val today = DateHelper.getTodayString()
            _currentDate.value = today
            repository.checkAndResetStreak(today)
            sendEvent(UiEvent.ShowToast("Reset system to current calendar date"))
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _currentDate.value = DateHelper.getTodayString()
            TodoWidgetProvider.triggerUpdate(application)
            sendEvent(UiEvent.ShowToast("Successfully reset database. Starting fresh!"))
        }
    }

    private fun sendEvent(event: UiEvent) {
        viewModelScope.launch {
            _uiEvent.emit(event)
        }
    }

    class Factory(
        private val application: Application,
        private val repository: TaskRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TaskViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class SaveSuccess(val newStreakCount: Int) : UiEvent
}
