package com.archery.tracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.archery.tracker.core.Arrow

@Entity(tableName = "rounds")
@TypeConverters(ArrowListConverter::class)
data class RoundEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val index: Int,
    val targetPosition: String,
    val arrows: List<Arrow>,
    val notes: String?,
    val updatedAt: String,
    val dirty: Boolean,
)
