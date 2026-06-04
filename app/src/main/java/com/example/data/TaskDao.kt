package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE dateString = :dateString ORDER BY hour ASC, minute ASC")
    fun getTasksForDate(dateString: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE dateString = :dateString ORDER BY hour ASC, minute ASC")
    suspend fun getTasksForDateList(dateString: String): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskByIdDirect(taskId: Int): Task?
 
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM streaks WHERE id = 1")
    fun getStreak(): Flow<Streak?>

    @Query("SELECT * FROM streaks WHERE id = 1")
    suspend fun getStreakDirect(): Streak?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStreak(streak: Streak)

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    @Query("DELETE FROM streaks")
    suspend fun clearAllStreaks()
}
