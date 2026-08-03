package com.archery.tracker.data.repository

import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionWithRounds
import com.archery.tracker.data.local.ArcheryDao
import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.remote.SyncRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class ArcheryRepository(
    private val dao: ArcheryDao,
    private val api: ArcheryApi,
) {

    fun sessions(): Flow<List<SessionWithRounds>> =
        dao.getAllSessions().map { sessionEntities ->
            sessionEntities.map { entity ->
                val rounds = dao.getRoundsForSession(entity.id).map { it.toDomain() }
                SessionWithRounds(entity.toDomain(), rounds)
            }
        }

    open suspend fun createSessionWithFirstRound(session: Session, firstRound: Round) {
        dao.upsertSession(session.toEntity(dirty = true))
        dao.upsertRound(firstRound.toEntity(dirty = true))
    }

    suspend fun saveRound(round: Round) {
        dao.upsertRound(round.toEntity(dirty = true))
    }

    suspend fun deleteSession(id: String): Result<Unit> = runCatching {
        val response = api.deleteSession(id)
        if (!response.isSuccessful) {
            error("Delete failed with status ${response.code()}")
        }
        dao.deleteRoundsForSession(id)
        dao.deleteSession(id)
    }

    suspend fun syncDirty(): Result<Unit> = runCatching {
        val dirtySessions = dao.getDirtySessions()
        val dirtyRounds = dao.getDirtyRounds()
        if (dirtySessions.isEmpty() && dirtyRounds.isEmpty()) return@runCatching

        api.sync(
            SyncRequestDto(
                sessions = dirtySessions.map { it.toDomain().toDto() },
                rounds = dirtyRounds.map { it.toDomain().toDto() },
            ),
        )

        dirtySessions.forEach { dao.clearSessionDirty(it.id) }
        dirtyRounds.forEach { dao.clearRoundDirty(it.id) }
    }

    suspend fun hasUnsyncedData(sessionId: String): Boolean {
        val sessionDirty = dao.getDirtySessions().any { it.id == sessionId }
        val roundsDirty = dao.getDirtyRounds().any { it.sessionId == sessionId }
        return sessionDirty || roundsDirty
    }

    suspend fun stats(
        type: String? = null, from: String? = null, to: String? = null,
        timeOfDay: String? = null, targetPosition: String? = null, arrowSet: String? = null,
    ): Result<StatsResponseDto> = runCatching {
        api.getStats(type, from, to, timeOfDay, targetPosition, arrowSet)
    }
}
