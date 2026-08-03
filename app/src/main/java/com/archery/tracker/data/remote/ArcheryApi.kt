package com.archery.tracker.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.POST

interface ArcheryApi {

    @GET("sessions")
    suspend fun listSessions(
        @Query("type") type: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("time_of_day") timeOfDay: String? = null,
        @Query("target_position") targetPosition: String? = null,
        @Query("arrow_set") arrowSet: String? = null,
    ): List<SessionWithRoundsDto>

    @PUT("sessions/{id}")
    suspend fun putSession(@Path("id") id: String, @Body session: SessionDto): SessionDto

    @DELETE("sessions/{id}")
    suspend fun deleteSession(@Path("id") id: String): Response<Unit>

    @PUT("rounds/{id}")
    suspend fun putRound(@Path("id") id: String, @Body round: RoundDto): RoundDto

    @POST("sync")
    suspend fun sync(@Body request: SyncRequestDto): SyncResponseDto

    @GET("stats")
    suspend fun getStats(
        @Query("type") type: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("time_of_day") timeOfDay: String? = null,
        @Query("target_position") targetPosition: String? = null,
        @Query("arrow_set") arrowSet: String? = null,
    ): StatsResponseDto
}
