package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val isCompleted: Boolean = false,
    val dateString: String, // Format: YYYY-MM-DD
    val slotCategory: String, // e.g. "Slot 1: 08AM to 02PM -- Backlog clear"
    val timeRange: String // e.g. "08AM to 10AM"
)
