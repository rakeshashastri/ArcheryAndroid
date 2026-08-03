package com.archery.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [SessionEntity::class, RoundEntity::class], version = 1, exportSchema = false)
@TypeConverters(ArrowListConverter::class)
abstract class ArcheryDatabase : RoomDatabase() {
    abstract fun archeryDao(): ArcheryDao
}
