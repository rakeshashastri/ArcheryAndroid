package com.archery.tracker.data.repository

import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.remote.RoundDto
import com.archery.tracker.data.remote.SessionDto
import com.archery.tracker.data.remote.SessionWithRoundsDto
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.remote.SyncRequestDto
import com.archery.tracker.data.remote.SyncResponseDto
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class FakeArcheryApi : ArcheryApi {
    var syncShouldFail = false
    var deleteShouldFail = false
    var listShouldFail = false
    var sessionsToReturn: List<SessionWithRoundsDto> = emptyList()
    val syncCalls = mutableListOf<SyncRequestDto>()
    val deleteCalls = mutableListOf<String>()

    override suspend fun listSessions(
        type: String?, from: String?, to: String?,
        timeOfDay: String?, targetPosition: String?, arrowSet: String?,
    ): List<SessionWithRoundsDto> {
        if (listShouldFail) throw java.io.IOException("network down")
        return sessionsToReturn
    }

    override suspend fun putSession(id: String, session: SessionDto): SessionDto = session

    override suspend fun deleteSession(id: String): Response<Unit> {
        deleteCalls.add(id)
        return if (deleteShouldFail) Response.error(500, "".toResponseBody(null))
        else Response.success<Unit>(204, null)
    }

    override suspend fun putRound(id: String, round: RoundDto): RoundDto = round

    override suspend fun sync(request: SyncRequestDto): SyncResponseDto {
        syncCalls.add(request)
        if (syncShouldFail) throw java.io.IOException("network down")
        return SyncResponseDto(sessions = request.sessions.size, rounds = request.rounds.size)
    }

    override suspend fun getStats(
        type: String?, from: String?, to: String?,
        timeOfDay: String?, targetPosition: String?, arrowSet: String?,
    ): StatsResponseDto = throw NotImplementedError("not needed by repository tests")
}
