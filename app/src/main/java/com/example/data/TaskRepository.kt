package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class TaskRepository(private val taskDao: TaskDao) {

    fun getTasksForDate(dateString: String): Flow<List<Task>> {
        return taskDao.getTasksForDate(dateString)
    }

    suspend fun checkAndPrepopulateTasksForDate(dateString: String): List<Task> {
        val existing = taskDao.getTasksForDateList(dateString)
        if (existing.isNotEmpty()) {
            var updated = false
            for (task in existing) {
                var currentTaskObj = task
                val normSlot = DateHelper.normalizeSlotName(task.slotCategory)
                if (task.slotCategory != normSlot) {
                    currentTaskObj = currentTaskObj.copy(slotCategory = normSlot)
                    taskDao.updateTask(currentTaskObj)
                    updated = true
                }

                if (currentTaskObj.title == "lecture 1") {
                    taskDao.updateTask(currentTaskObj.copy(title = "Physics Question practice"))
                    updated = true
                } else if (currentTaskObj.title == "lecture 2") {
                    taskDao.updateTask(currentTaskObj.copy(title = "Physical chemistry lecture"))
                    updated = true
                } else if (currentTaskObj.title == "lecture 3") {
                    taskDao.updateTask(currentTaskObj.copy(title = "Maths lecture"))
                    updated = true
                } else if (currentTaskObj.title == "previous day revision" && currentTaskObj.timeRange == "02PM to 04PM") {
                    taskDao.updateTask(currentTaskObj.copy(title = "last day revision"))
                    updated = true
                } else if (currentTaskObj.title == "2nd lecture H.W/DPP" || currentTaskObj.title == "2st lecture H.W/DPP") {
                    if (currentTaskObj.title != "2st lecture H.W/DPP") {
                        taskDao.updateTask(currentTaskObj.copy(title = "2st lecture H.W/DPP"))
                        updated = true
                    }
                } else if (currentTaskObj.title == "3rd lecture H.W/DPP" || currentTaskObj.title == "3st lecture H.W/DPP") {
                    if (currentTaskObj.title != "3st lecture H.W/DPP" || currentTaskObj.timeRange != "11PM to 12PM") {
                        taskDao.updateTask(currentTaskObj.copy(title = "3st lecture H.W/DPP", timeRange = "11PM to 12PM"))
                        updated = true
                    }
                }
            }
            if (updated) {
                return taskDao.getTasksForDateList(dateString)
            }
        }
        if (existing.isEmpty()) {
            val defaultTasks = listOf(
                Task(
                    title = "Physics Question practice",
                    timeRange = "07AM to 09AM",
                    hour = 7,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 1: 07AM to 02PM -- Backlog clear"
                ),
                Task(
                    title = "Physical chemistry lecture",
                    timeRange = "09AM to 11AM",
                    hour = 9,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 1: 07AM to 02PM -- Backlog clear"
                ),
                Task(
                    title = "Break",
                    timeRange = "11AM to 12PM",
                    hour = 11,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 1: 07AM to 02PM -- Backlog clear"
                ),
                Task(
                    title = "Maths lecture",
                    timeRange = "12PM to 02PM",
                    hour = 12,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 1: 07AM to 02PM -- Backlog clear"
                ),
                Task(
                    title = "last day revision",
                    timeRange = "02PM to 04PM",
                    hour = 14,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 2: 02PM to 04PM -- Revision"
                ),
                Task(
                    title = "live lectures",
                    timeRange = "04PM to 09PM",
                    hour = 16,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 3: 04PM to 09PM -- Classes"
                ),
                Task(
                    title = "1st lecture H.W/DPP",
                    timeRange = "09PM to 10PM",
                    hour = 21,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 4: 09PM to 12AM -- Questions and H.W"
                ),
                Task(
                    title = "2st lecture H.W/DPP",
                    timeRange = "10PM to 11PM",
                    hour = 22,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 4: 09PM to 12AM -- Questions and H.W"
                ),
                Task(
                    title = "3st lecture H.W/DPP",
                    timeRange = "11PM to 12PM",
                    hour = 23,
                    minute = 0,
                    isCompleted = false,
                    dateString = dateString,
                    slotCategory = "Slot 4: 09PM to 12AM -- Questions and H.W"
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
