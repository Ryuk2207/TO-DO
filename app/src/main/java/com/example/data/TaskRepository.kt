package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class TaskRepository(private val taskDao: TaskDao) {

    fun getTasksForDate(dateString: String): Flow<List<Task>> {
        return taskDao.getTasksForDate(dateString)
    }

    suspend fun checkAndPrepopulateTasksForDate(dateString: String): List<Task> {
        val existing = taskDao.getTasksForDateList(dateString)
        
        // Identify if the user's daily tasks belong to any of the old schedule formats
        val hasOldSlots = existing.any { 
            it.slotCategory.contains("07AM to 02PM") || 
            it.slotCategory.contains("02PM to 04PM") || 
            it.slotCategory.contains("04PM to 09PM") || 
            it.slotCategory.contains("09PM to 12AM") ||
            it.slotCategory.contains("9 AM to 1PM") ||
            it.slotCategory.contains("1 PM to 2 PM") ||
            it.slotCategory.contains("2 PM to 4 PM") ||
            it.slotCategory.contains("4 PM to 7 PM") ||
            it.slotCategory.contains("7 PM to 11 PM") ||
            it.slotCategory.contains("8AM to 12PM") ||
            it.slotCategory.contains("12PM to 1PM") ||
            it.slotCategory.contains("1PM to 5PM") ||
            it.slotCategory.contains("5PM to 11PM")
        }
        
        if (existing.isEmpty() || hasOldSlots) {
            // Remove the old system template tasks to avoid collision with the new layout
            if (hasOldSlots) {
                for (task in existing) {
                    if (task.slotCategory.contains("07AM to 02PM") || 
                        task.slotCategory.contains("02PM to 04PM") || 
                        task.slotCategory.contains("04PM to 09PM") || 
                        task.slotCategory.contains("09PM to 12AM") ||
                        task.slotCategory.contains("9 AM to 1PM") ||
                        task.slotCategory.contains("1 PM to 2 PM") ||
                        task.slotCategory.contains("2 PM to 4 PM") ||
                        task.slotCategory.contains("4 PM to 7 PM") ||
                        task.slotCategory.contains("7 PM to 11 PM") ||
                        task.slotCategory.contains("8AM to 12PM") ||
                        task.slotCategory.contains("12PM to 1PM") ||
                        task.slotCategory.contains("1PM to 5PM") ||
                        task.slotCategory.contains("5PM to 11PM")) {
                        taskDao.deleteTask(task)
                    }
                }
            }
            
            val defaultTasks = listOf(
                // Slot 1: 9AM to 11AM -- Question Practice , 1 Lecture
                Task(
                    title = "Electrostatic Questions/Revision",
                    timeRange = "9AM to 10AM",
                    hour = 9,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 1: 9AM to 11AM -- Question Practice , 1 Lecture"
                ),
                Task(
                    title = "Matrices and Determinant Questions/Revision",
                    timeRange = "10AM to 11AM",
                    hour = 10,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 1: 9AM to 11AM -- Question Practice , 1 Lecture"
                ),
                Task(
                    title = "Physics Lecture",
                    timeRange = "11AM to 1PM",
                    hour = 11,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 1: 9AM to 11AM -- Question Practice , 1 Lecture"
                ),
                // Slot 2: 1PM to 2PM -- Break
                Task(
                    title = "Break",
                    timeRange = "1PM to 2PM",
                    hour = 13,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 2: 1PM to 2PM -- Break"
                ),
                // Slot 3: 2PM to 6PM -- 2 Classes
                Task(
                    title = "Physical Chemistry Lecture",
                    timeRange = "2PM to 4PM",
                    hour = 14,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 3: 2PM to 6PM -- 2 Classes"
                ),
                Task(
                    title = "Maths Lecture",
                    timeRange = "4PM to 6PM",
                    hour = 16,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 3: 2PM to 6PM -- 2 Classes"
                ),
                // Slot 4: 6PM to 7PM -- Question Practice/Revision
                Task(
                    title = "Solution Questions/Revision",
                    timeRange = "6PM to 7PM",
                    hour = 18,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 4: 6PM to 7PM -- Question Practice/Revision"
                ),
                // Slot 5: 7PM to 8PM -- Break
                Task(
                    title = "Break",
                    timeRange = "7PM to 8PM",
                    hour = 19,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 5: 7PM to 8PM -- Break"
                ),
                // Slot 6: 8PM to 11PM -- Lecture Dpp/H.W
                Task(
                    title = "Physics Lecture Dpp/H.W",
                    timeRange = "8PM to 9PM",
                    hour = 20,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 6: 8PM to 11PM -- Lecture Dpp/H.W"
                ),
                Task(
                    title = "Maths Lecture Dpp/H.W",
                    timeRange = "9PM to 10PM",
                    hour = 21,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 6: 8PM to 11PM -- Lecture Dpp/H.W"
                ),
                Task(
                    title = "Physical chemistry Lecture Dpp/H.W",
                    timeRange = "10PM to 11PM",
                    hour = 22,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 6: 8PM to 11PM -- Lecture Dpp/H.W"
                )
            )
            for (task in defaultTasks) {
                taskDao.insertTask(task)
            }
            return taskDao.getTasksForDateList(dateString)
        }
        return existing
    }

    suspend fun getTasksForDateList(dateString: String): List<Task> {
        checkAndPrepopulateTasksForDate(dateString)
        return taskDao.getTasksForDateList(dateString)
    }

    suspend fun insertTask(task: Task) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
    }

    fun getStreak(): Flow<Streak?> {
        return taskDao.getStreak()
    }

    /**
     * Checks if the streak needs to be reset based on the current date of reference.
     * Call this when the app loads or during simulation.
     */
    suspend fun checkAndResetStreak(currentDateString: String) {
        val currentStreak = taskDao.getStreakDirect() ?: Streak(streakCount = 0)
        val lastCompleted = currentStreak.lastCompletedDateString

        if (lastCompleted.isNotEmpty()) {
            // Streak is alive if last completed is either TODAY or YESTERDAY.
            // If it's earlier, then the streak was broken because the user didn't complete and save yesterday's tasks.
            val yesterday = DateHelper.getYesterdayString(currentDateString)
            if (lastCompleted != currentDateString && lastCompleted != yesterday) {
                // Streak broken! Reset to 0
                val resetStreak = currentStreak.copy(streakCount = 0)
                taskDao.insertOrUpdateStreak(resetStreak)
            }
        }
    }

    /**
     * Attempts to register completion for a given date.
     * Returns a Result detailing success or reason for failure.
     */
    suspend fun saveCurrentDayTasks(dateString: String): SaveResult {
        val tasks = taskDao.getTasksForDateList(dateString)
        if (tasks.isEmpty()) {
            return SaveResult.Error("Please create at least one task before saving your day!")
        }

        val allCompleted = tasks.all { it.isCompleted }
        if (!allCompleted) {
            return SaveResult.Error("Cannot save: not all tasks are marked as completed yet!")
        }

        // Proceed to update streak
        val currentStreak = taskDao.getStreakDirect() ?: Streak(streakCount = 0)
        val lastCompleted = currentStreak.lastCompletedDateString

        if (lastCompleted == dateString) {
            return SaveResult.AlreadyDone("You have already saved and recorded your streak for this day!")
        }

        val yesterday = DateHelper.getYesterdayString(dateString)
        val newStreakCount = when {
            lastCompleted.isEmpty() -> {
                // First completion ever
                1
            }
            lastCompleted == yesterday -> {
                // Consecutive day!
                currentStreak.streakCount + 1
            }
            else -> {
                // Streak was broken or skipped, starts fresh at 1
                1
            }
        }

        val updatedStreak = currentStreak.copy(
            streakCount = newStreakCount,
            lastCompletedDateString = dateString
        )
        taskDao.insertOrUpdateStreak(updatedStreak)

        return SaveResult.Success(newStreakCount)
    }

    suspend fun clearAllData() {
        taskDao.clearAllTasks()
        taskDao.clearAllStreaks()
        taskDao.clearAllNotifications()
    }

    suspend fun getAllTasksDirect(): List<Task> {
        return taskDao.getAllTasksDirect()
    }

    suspend fun getAllNotificationsDirect(): List<InAppNotificationEntity> {
        return taskDao.getAllNotificationsDirect()
    }

    suspend fun getStreakDirect(): Streak? {
        return taskDao.getStreakDirect()
    }

    suspend fun insertOrUpdateStreak(streak: Streak) {
        taskDao.insertOrUpdateStreak(streak)
    }

    fun getAllNotifications(): Flow<List<InAppNotificationEntity>> {
        return taskDao.getAllNotifications()
    }

    suspend fun insertNotification(notification: InAppNotificationEntity) {
        taskDao.insertNotification(notification)
    }

    suspend fun deleteNotification(notification: InAppNotificationEntity) {
        taskDao.deleteNotification(notification)
    }

    suspend fun deleteNotificationById(id: Int) {
        taskDao.deleteNotificationById(id)
    }

    suspend fun clearAllNotifications() {
        taskDao.clearAllNotifications()
    }
}

sealed interface SaveResult {
    data class Success(val newStreak: Int) : SaveResult
    data class AlreadyDone(val message: String) : SaveResult
    data class Error(val message: String) : SaveResult
}
