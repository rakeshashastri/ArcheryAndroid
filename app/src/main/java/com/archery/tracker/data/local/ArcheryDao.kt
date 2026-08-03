package com.archery.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArcheryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRound(round: RoundEntity)

    @Query("SELECT * FROM sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM rounds WHERE sessionId = :sessionId ORDER BY `index` ASC")
    suspend fun getRoundsForSession(sessionId: String): List<RoundEntity>

    @Query("SELECT * FROM sessions WHERE dirty = 1")
    suspend fun getDirtySessions(): List<SessionEntity>

    @Query("SELECT * FROM rounds WHERE dirty = 1")
    suspend fun getDirtyRounds(): List<RoundEntity>

    @Query("UPDATE sessions SET dirty = 0 WHERE id = :id")
    suspend fun clearSessionDirty(id: String)

    @Query("UPDATE rounds SET dirty = 0 WHERE id = :id")
    suspend fun clearRoundDirty(id: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM rounds WHERE sessionId = :sessionId")
    suspend fun deleteRoundsForSession(sessionId: String)
}
