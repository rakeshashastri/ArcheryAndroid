package com.archery.tracker.data.repository

import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionWithRounds
import com.archery.tracker.data.local.ArcheryDao
import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.remote.SyncRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class SessionListEntry(val sessionWithRounds: SessionWithRounds, val isDirty: Boolean)

open class ArcheryRepository(
    private val dao: ArcheryDao,
    private val api: ArcheryApi,
) {

    /**
     * Sessions with their rounds and per-session sync status, recomputed whenever either the
     * sessions or the rounds table changes. Dirty state comes from the same query pass, so callers
     * that need it (the history list) don't issue extra dirty-scan queries per emission.
     */
    fun sessionList(): Flow<List<SessionListEntry>> =
        combine(dao.getAllSessions(), dao.getAllRounds()) { sessionEntities, roundEntities ->
            val roundsBySession = roundEntities.groupBy { it.sessionId }
            sessionEntities.map { entity ->
                val roundEntities = (roundsBySession[entity.id] ?: emptyList()).sortedBy { it.index }
                val isDirty = entity.dirty || roundEntities.any { it.dirty }
                SessionListEntry(
                    SessionWithRounds(entity.toDomain(), roundEntities.map { it.toDomain() }),
                    isDirty,
                )
            }
        }

    fun sessions(): Flow<List<SessionWithRounds>> =
        sessionList().map { entries -> entries.map { it.sessionWithRounds } }

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

    suspend fun stats(
        type: String? = null, from: String? = null, to: String? = null,
        timeOfDay: String? = null, targetPosition: String? = null, arrowSet: String? = null,
    ): Result<StatsResponseDto> = runCatching {
        api.getStats(type, from, to, timeOfDay, targetPosition, arrowSet)
    }
}
