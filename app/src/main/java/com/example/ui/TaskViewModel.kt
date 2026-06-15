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
                val list = repository.checkAndPrepopulateTasksForDate(date, slotCategories.value)
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

    // Persistent in-app notifications
    val notifications: StateFlow<List<InAppNotificationEntity>> = repository.getAllNotifications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Operation messages/alerts (Toasts/Snackbars)
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private var lastRecordedSystemDate = DateHelper.getTodayString()

    val slotCategories = MutableStateFlow<List<String>>(emptyList())

    init {
        // Load custom slots
        loadSlotCategories()

        // Initial check for streak break when launched
        viewModelScope.launch {
            repository.checkAndResetStreak(_currentDate.value)
        }

        // One-time automatic increment by 1 as requested by the user
        viewModelScope.launch {
            val sharedPrefs = application.getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE)
            val appliedKey = "streak_one_time_increment_applied_v3"
            if (!sharedPrefs.getBoolean(appliedKey, false)) {
                try {
                    val db = com.example.data.AppDatabase.getDatabase(application)
                    val dao = db.taskDao()
                    val currentStreak = dao.getStreakDirect() ?: Streak(streakCount = 0)
                    
                    val todayStr = DateHelper.getTodayString()
                    val yesterdayStr = DateHelper.getYesterdayString(todayStr)
                    
                    val updatedStreak = if (currentStreak.lastCompletedDateString == todayStr) {
                        currentStreak.copy(
                            streakCount = currentStreak.streakCount + 1,
                            lastCompletedDateString = todayStr
                        )
                    } else {
                        Streak(
                            id = 1,
                            streakCount = currentStreak.streakCount + 1,
                            lastCompletedDateString = yesterdayStr
                        )
                    }
                    
                    dao.insertOrUpdateStreak(updatedStreak)
                    TodoWidgetProvider.triggerUpdate(application)
                    sharedPrefs.edit().putBoolean(appliedKey, true).apply()
                    sendEvent(UiEvent.ShowToast("Daily streak automatically increased by 1!"))
                } catch (e: Exception) {
                    android.util.Log.e("TaskViewModel", "Error applying one-time streak increment", e)
                }
            }
        }

        // Continuous check to automatically refresh date and tasks at 12 midnight
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15000)
                val systemDate = DateHelper.getTodayString()
                if (systemDate != lastRecordedSystemDate) {
                    lastRecordedSystemDate = systemDate
                    _currentDate.value = systemDate
                    repository.checkAndResetStreak(systemDate)
                    TodoWidgetProvider.triggerUpdate(application)
                    sendEvent(UiEvent.ShowToast("A new day has started! Date and tasks refreshed."))
                }
            }
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
            val updatedTasks = repository.getTasksForDateList(date, slotCategories.value)
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

    /**
     * Force checks the current date against natural system date and refreshes if a new day has started.
     */
    fun refreshToTodayIfNeeded() {
        val today = DateHelper.getTodayString()
        if (lastRecordedSystemDate != today) {
            lastRecordedSystemDate = today
        }
        if (_currentDate.value != today) {
            _currentDate.value = today
            viewModelScope.launch {
                repository.checkAndResetStreak(today)
                TodoWidgetProvider.triggerUpdate(application)
                sendEvent(UiEvent.ShowToast("A new day has started! Date and tasks refreshed."))
            }
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

    fun deleteNotification(notification: InAppNotificationEntity) {
        viewModelScope.launch {
            repository.deleteNotification(notification)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    fun exportData(context: android.content.Context, outputStream: java.io.OutputStream, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val json = org.json.JSONObject()

                // 1. SharedPreferences Settings
                val sharedPrefs = context.getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE)
                val settings = org.json.JSONObject().apply {
                    put("wallpaper_enabled", sharedPrefs.getBoolean("wallpaper_enabled", true))
                    put("wallpaper_style", sharedPrefs.getString("wallpaper_style", "liquid_glass") ?: "liquid_glass")
                    put("custom_wallpaper_url", sharedPrefs.getString("custom_wallpaper_url", "") ?: "")
                    put("vibrations_enabled", sharedPrefs.getBoolean("vibrations_enabled", true))
                    put("sounds_enabled", sharedPrefs.getBoolean("sounds_enabled", true))
                    put("notifications_enabled", sharedPrefs.getBoolean("notifications_enabled", true))
                    put("streak_one_time_increment_applied_v3", sharedPrefs.getBoolean("streak_one_time_increment_applied_v3", false))
                    put("slot_categories", sharedPrefs.getString("slot_categories", "") ?: "")
                }
                json.put("settings", settings)

                // 2. Streaks from DB
                val streak = repository.getStreakDirect()
                if (streak != null) {
                    val streakJson = org.json.JSONObject().apply {
                        put("streakCount", streak.streakCount)
                        put("lastCompletedDateString", streak.lastCompletedDateString)
                    }
                    json.put("streak", streakJson)
                }

                // 3. Tasks from DB
                val tasks = repository.getAllTasksDirect()
                val tasksArray = org.json.JSONArray()
                for (task in tasks) {
                    val taskJson = org.json.JSONObject().apply {
                        put("title", task.title)
                        put("hour", task.hour)
                        put("minute", task.minute)
                        put("isCompleted", task.isCompleted)
                        put("dateString", task.dateString)
                        put("slotCategory", task.slotCategory)
                        put("timeRange", task.timeRange)
                    }
                    tasksArray.put(taskJson)
                }
                json.put("tasks", tasksArray)

                // 4. Notifications from DB
                val notifications = repository.getAllNotificationsDirect()
                val notificationsArray = org.json.JSONArray()
                for (notif in notifications) {
                    val notifJson = org.json.JSONObject().apply {
                        put("title", notif.title)
                        put("text", notif.text)
                        put("time", notif.time)
                        put("timestamp", notif.timestamp)
                    }
                    notificationsArray.put(notifJson)
                }
                json.put("notifications", notificationsArray)

                // 5. Custom Wallpaper file (Base64)
                val filesDir = context.filesDir
                val customWallpaperFile = filesDir.listFiles()?.find { it.name.startsWith("custom_wallpaper.") }
                if (customWallpaperFile != null && customWallpaperFile.exists()) {
                    try {
                        val bytes = customWallpaperFile.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        json.put("custom_wallpaper_base64", base64)
                        json.put("custom_wallpaper_ext", customWallpaperFile.extension)
                    } catch (fileEx: Exception) {
                        android.util.Log.e("TaskViewModel", "Error reading custom wallpaper file", fileEx)
                    }
                }

                // Write out JSON
                outputStream.use { out ->
                    out.write(json.toString(4).toByteArray(Charsets.UTF_8))
                }

                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "Export failed", e)
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun importData(
        context: android.content.Context,
        inputStream: java.io.InputStream,
        onSettingsLoaded: (
            wallpaperEnabled: Boolean,
            wallpaperStyle: String,
            customWallpaperUrl: String,
            vibrationsEnabled: Boolean,
            soundsEnabled: Boolean,
            notificationsEnabled: Boolean
        ) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Read input JSON
                val jsonString = inputStream.use { stream ->
                    stream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                }
                val json = org.json.JSONObject(jsonString)

                // Step 1: Wipe all old data from database first
                repository.clearAllData()

                // Step 2: Restore Streak
                if (json.has("streak")) {
                    val streakJson = json.getJSONObject("streak")
                    val streak = com.example.data.Streak(
                        id = 1,
                        streakCount = streakJson.optInt("streakCount", 0),
                        lastCompletedDateString = streakJson.optString("lastCompletedDateString", "")
                    )
                    repository.insertOrUpdateStreak(streak)
                }

                // Step 3: Restore Tasks
                if (json.has("tasks")) {
                    val tasksArray = json.getJSONArray("tasks")
                    for (i in 0 until tasksArray.length()) {
                        val taskJson = tasksArray.getJSONObject(i)
                        val task = com.example.data.Task(
                            id = 0, // autoGenerate on insertion
                            title = taskJson.optString("title", ""),
                            hour = taskJson.optInt("hour", 12),
                            minute = taskJson.optInt("minute", 0),
                            isCompleted = taskJson.optBoolean("isCompleted", false),
                            dateString = taskJson.optString("dateString", ""),
                            slotCategory = taskJson.optString("slotCategory", "Custom Tasks"),
                            timeRange = taskJson.optString("timeRange", "12:00")
                        )
                        repository.insertTask(task)
                    }
                }

                // Step 4: Restore Notifications
                if (json.has("notifications")) {
                    val notificationsArray = json.getJSONArray("notifications")
                    for (i in 0 until notificationsArray.length()) {
                        val notifJson = notificationsArray.getJSONObject(i)
                        val notification = com.example.data.InAppNotificationEntity(
                            id = 0,
                            title = notifJson.optString("title", ""),
                            text = notifJson.optString("text", ""),
                            time = notifJson.optString("time", ""),
                            timestamp = notifJson.optLong("timestamp", System.currentTimeMillis())
                        )
                        repository.insertNotification(notification)
                    }
                }

                // Step 5: Restore SharedPreferences Settings
                val sharedPrefs = context.getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE)
                val editor = sharedPrefs.edit()

                var wallpaperEnabled = true
                var wallpaperStyle = "liquid_glass"
                var customWallpaperUrl = ""
                var vibrationsEnabled = true
                var soundsEnabled = true
                var notificationsEnabled = true
                var slotCategoriesStr = ""

                if (json.has("settings")) {
                    val settings = json.getJSONObject("settings")
                    
                    wallpaperEnabled = settings.optBoolean("wallpaper_enabled", true)
                    wallpaperStyle = settings.optString("wallpaper_style", "liquid_glass")
                    customWallpaperUrl = settings.optString("custom_wallpaper_url", "")
                    vibrationsEnabled = settings.optBoolean("vibrations_enabled", true)
                    soundsEnabled = settings.optBoolean("sounds_enabled", true)
                    notificationsEnabled = settings.optBoolean("notifications_enabled", true)
                    val streakOneTime = settings.optBoolean("streak_one_time_increment_applied_v3", false)
                    slotCategoriesStr = settings.optString("slot_categories", "")

                    editor.putBoolean("wallpaper_enabled", wallpaperEnabled)
                    editor.putString("wallpaper_style", wallpaperStyle)
                    editor.putString("custom_wallpaper_url", customWallpaperUrl)
                    editor.putBoolean("vibrations_enabled", vibrationsEnabled)
                    editor.putBoolean("sounds_enabled", soundsEnabled)
                    editor.putBoolean("notifications_enabled", notificationsEnabled)
                    editor.putBoolean("streak_one_time_increment_applied_v3", streakOneTime)
                    if (slotCategoriesStr.isNotEmpty()) {
                        editor.putString("slot_categories", slotCategoriesStr)
                    }
                }

                // Step 6: Custom Wallpaper file
                if (json.has("custom_wallpaper_base64")) {
                    val base64 = json.getString("custom_wallpaper_base64")
                    val ext = json.optString("custom_wallpaper_ext", "jpg")
                    try {
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                        context.filesDir.listFiles()?.forEach { file ->
                            if (file.name.startsWith("custom_wallpaper.")) {
                                file.delete()
                            }
                        }
                        val destFile = java.io.File(context.filesDir, "custom_wallpaper.$ext")
                        destFile.writeBytes(bytes)
                        
                        customWallpaperUrl = destFile.absolutePath
                        editor.putString("custom_wallpaper_url", customWallpaperUrl)
                    } catch (wallpaperEx: Exception) {
                        android.util.Log.e("TaskViewModel", "Error restoring custom wallpaper file", wallpaperEx)
                    }
                }

                editor.apply()

                // Update Widget provider to refresh the widget automatically with restored data!
                TodoWidgetProvider.triggerUpdate(context)

                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    loadSlotCategories()
                    onSettingsLoaded(
                        wallpaperEnabled,
                        wallpaperStyle,
                        customWallpaperUrl,
                        vibrationsEnabled,
                        soundsEnabled,
                        notificationsEnabled
                    )
                    onSuccess()
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "Import failed", e)
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun loadSlotCategories() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE)
        val serialized = sharedPrefs.getString("slot_categories", null)
        val defaults = listOf(
            "Slot 1: 07AM to 02PM -- Backlog clear",
            "Slot 2: 02PM to 04PM -- Revision",
            "Slot 3: 04PM to 09PM -- Classes",
            "Slot 4: 09PM to 12AM -- Questions and H.W",
            "Custom Tasks"
        )
        if (serialized == null) {
            saveSlotsToPrefs(defaults)
        } else {
            try {
                val array = org.json.JSONArray(serialized)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val raw = array.getString(i)
                    val norm = com.example.data.DateHelper.normalizeSlotName(raw)
                    list.add(norm)
                }
                
                if (!list.contains("Custom Tasks")) {
                    list.add("Custom Tasks")
                }
                
                val distinctList = list.distinct()
                saveSlotsToPrefs(distinctList)
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "Error loading slots", e)
                saveSlotsToPrefs(defaults)
            }
        }
    }

    private fun saveSlotsToPrefs(list: List<String>) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE)
        val array = org.json.JSONArray()
        list.forEach { array.put(it) }
        sharedPrefs.edit().putString("slot_categories", array.toString()).apply()
        slotCategories.value = list
    }

    fun addSlotCategory(newSlot: String) {
        if (newSlot.isBlank()) return
        val currentList = slotCategories.value.toMutableList()
        if (currentList.contains(newSlot)) {
            sendEvent(UiEvent.ShowToast("Slot already exists!"))
            return
        }
        val customIdx = currentList.indexOf("Custom Tasks")
        if (customIdx >= 0) {
            currentList.add(customIdx, newSlot)
        } else {
            currentList.add(newSlot)
        }
        saveSlotsToPrefs(currentList)
        sendEvent(UiEvent.ShowToast("Slot category added!"))
    }

    fun removeSlotCategory(slotToDelete: String) {
        if (slotToDelete == "Custom Tasks") {
            sendEvent(UiEvent.ShowToast("Cannot delete Custom Tasks slot"))
            return
        }
        val currentList = slotCategories.value.toMutableList()
        if (currentList.remove(slotToDelete)) {
            saveSlotsToPrefs(currentList)
            viewModelScope.launch {
                val date = _currentDate.value
                val tasks = repository.getTasksForDateList(date, slotCategories.value)
                tasks.forEach { task ->
                    if (task.slotCategory == slotToDelete) {
                        repository.updateTask(task.copy(slotCategory = "Custom Tasks"))
                    }
                }
                TodoWidgetProvider.triggerUpdate(getApplication())
                sendEvent(UiEvent.ShowToast("Slot removed and tasks moved to Custom Tasks"))
            }
        }
    }

    fun renameSlotCategory(oldSlot: String, newSlot: String) {
        if (newSlot.isBlank() || oldSlot == newSlot) return
        if (oldSlot == "Custom Tasks") {
            sendEvent(UiEvent.ShowToast("Cannot rename Custom Tasks slot"))
            return
        }
        val currentList = slotCategories.value.toMutableList()
        val index = currentList.indexOf(oldSlot)
        if (index >= 0) {
            if (currentList.contains(newSlot)) {
                sendEvent(UiEvent.ShowToast("Slot already exists!"))
                return
            }
            currentList[index] = newSlot
            saveSlotsToPrefs(currentList)
            viewModelScope.launch {
                val allTasks = repository.getAllTasksDirect()
                allTasks.forEach { task ->
                    if (task.slotCategory == oldSlot) {
                        repository.updateTask(task.copy(slotCategory = newSlot))
                    }
                }
                TodoWidgetProvider.triggerUpdate(getApplication())
                sendEvent(UiEvent.ShowToast("Slot renamed and tasks updated!"))
            }
        }
    }

    fun updateTaskDetails(task: Task, title: String, hour: Int, minute: Int, slotCategory: String) {
        viewModelScope.launch {
            AlarmScheduler.cancelAlarm(getApplication(), task)
            val updated = task.copy(
                title = title,
                hour = hour,
                minute = minute,
                slotCategory = slotCategory,
                timeRange = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
            )
            repository.updateTask(updated)
            if (!updated.isCompleted) {
                AlarmScheduler.scheduleAlarm(getApplication(), updated)
            }
            TodoWidgetProvider.triggerUpdate(getApplication())
            sendEvent(UiEvent.ShowToast("Task updated!"))
        }
    }

    fun addTaskWithSlot(title: String, hour: Int, minute: Int, slotCategory: String) {
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
                slotCategory = slotCategory,
                timeRange = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
            )
            repository.insertTask(newTask)
            
            val updatedTasks = repository.getTasksForDateList(date, slotCategories.value)
            val insertedTask = updatedTasks.find { 
                it.title == title && it.hour == hour && it.minute == minute && it.slotCategory == slotCategory && !it.isCompleted 
            }
            if (insertedTask != null) {
                AlarmScheduler.scheduleAlarm(getApplication(), insertedTask)
            }
            TodoWidgetProvider.triggerUpdate(getApplication())
            sendEvent(UiEvent.ShowToast("Task added to $slotCategory!"))
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
