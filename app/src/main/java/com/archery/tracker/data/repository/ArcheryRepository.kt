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
import java.time.Instant

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

    /**
     * Push local changes up, then pull the server's state down and merge — a full two-way sync.
     * The set of sessions that had unpushed (dirty) work is captured BEFORE the push clears those
     * flags, so the pull's deletion step still protects them even though the push just marked them
     * clean (otherwise a session deleted elsewhere mid-edit could wipe the edit we just uploaded).
     */
    suspend fun sync(): Result<Unit> {
        val protectedSessionIds = buildSet {
            dao.getDirtySessions().forEach { add(it.id) }
            dao.getDirtyRounds().forEach { add(it.sessionId) }
        }
        val push = syncDirty()
        val pull = pullAndMerge(protectedSessionIds)
        return if (push.isSuccess && pull.isSuccess) Result.success(Unit)
        else Result.failure(push.exceptionOrNull() ?: pull.exceptionOrNull() ?: IllegalStateException("sync failed"))
    }

    /**
     * Pulls the server's sessions and merges them into Room so sessions created on another device
     * (e.g. the web client) show up here. Merge rules:
     *  - A row not present locally is inserted (clean).
     *  - A non-dirty local row is overwritten only when the server's updatedAt is newer (last-write-wins).
     *  - A dirty local row (an unpushed local edit) is never overwritten — its pending push wins.
     *  - A non-dirty local row absent from the server was deleted elsewhere, so it is removed here.
     *    Sessions with any unpushed (dirty) round, or in [protectedSessionIds], are kept so a pending
     *    edit is never lost to a cascade.
     */
    suspend fun pullAndMerge(protectedSessionIds: Set<String> = emptySet()): Result<Unit> = runCatching {
        val server = api.listSessions()
        val localSessions = dao.getAllSessionsOnce()
        val localRounds = dao.getAllRoundsOnce()

        val serverSessionIds = server.mapTo(HashSet()) { it.id }
        val serverRounds = server.flatMap { it.rounds }
        val serverRoundIds = serverRounds.mapTo(HashSet()) { it.id }
        val localSessionById = localSessions.associateBy { it.id }
        val localRoundById = localRounds.associateBy { it.id }

        for (s in server) {
            val local = localSessionById[s.id]
            if (local == null || (!local.dirty && isNewer(s.updatedAt, local.updatedAt))) {
                dao.upsertSession(s.toSessionEntity(dirty = false))
            }
        }
        for (r in serverRounds) {
            val local = localRoundById[r.id]
            if (local == null || (!local.dirty && isNewer(r.updatedAt, local.updatedAt))) {
                dao.upsertRound(r.toEntity(dirty = false))
            }
        }

        for (local in localSessions) {
            val hasDirtyRound = localRounds.any { it.sessionId == local.id && it.dirty }
            val protected = local.dirty || hasDirtyRound || local.id in protectedSessionIds
            if (!protected && local.id !in serverSessionIds) {
                dao.deleteRoundsForSession(local.id)
                dao.deleteSession(local.id)
            }
        }
        for (local in localRounds) {
            if (!local.dirty && local.id !in serverRoundIds && local.sessionId in serverSessionIds) {
                dao.deleteRound(local.id)
            }
        }
    }

    /**
     * Whether [server] is strictly later than [local]. Both are ISO-8601 instants, but
     * Instant.toString() emits a variable-width fractional-second field, so a raw string compare
     * can invert order (e.g. "…100Z" vs "…100001Z"). Parse and compare as instants; fall back to a
     * string compare only if a timestamp is unparseable.
     */
    private fun isNewer(server: String, local: String): Boolean =
        runCatching { Instant.parse(server).isAfter(Instant.parse(local)) }
            .getOrElse { server > local }

    suspend fun stats(
        type: String? = null, from: String? = null, to: String? = null,
        timeOfDay: String? = null, targetPosition: String? = null, arrowSet: String? = null,
    ): Result<StatsResponseDto> = runCatching {
        api.getStats(type, from, to, timeOfDay, targetPosition, arrowSet)
    }
}
