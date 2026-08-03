package com.archery.tracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val date: String,
    val type: String,
    val timeOfDay: String,
    val arrowSet: String,
    val poundage: Double,
    val notes: String?,
    val updatedAt: String,
    val dirty: Boolean,
)
