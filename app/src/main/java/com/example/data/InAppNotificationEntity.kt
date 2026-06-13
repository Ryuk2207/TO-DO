package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "in_app_notifications")
data class InAppNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val text: String,
    val time: String,
    val timestamp: Long = System.currentTimeMillis()
)
