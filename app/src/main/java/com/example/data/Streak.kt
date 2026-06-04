package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streaks")
data class Streak(
    @PrimaryKey val id: Int = 1,
    val streakCount: Int = 0,
    val lastCompletedDateString: String = "" // Format: YYYY-MM-DD
)
