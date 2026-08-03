package com.archery.tracker.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ArrowDto(val value: Int, val isX: Boolean)

@Serializable
data class SessionDto(
    val id: String,
    val userId: String,
    val date: String,
    val type: String,
    val timeOfDay: String,
    val arrowSet: String,
    val poundage: Double,
    val notes: String?,
    val updatedAt: String,
)

@Serializable
data class RoundDto(
    val id: String,
    val sessionId: String,
    val index: Int,
    val targetPosition: String,
    val arrows: List<ArrowDto>,
    val notes: String?,
    val updatedAt: String,
)

@Serializable
data class SessionWithRoundsDto(
    val id: String,
    val userId: String,
    val date: String,
    val type: String,
    val timeOfDay: String,
    val arrowSet: String,
    val poundage: Double,
    val notes: String?,
    val updatedAt: String,
    val rounds: List<RoundDto>,
)

@Serializable
data class SeriesPointDto(val date: String, val value: Double)

@Serializable
data class RoundPointDto(
    val roundId: String,
    val sessionId: String,
    val roundIndex: Int,
    val date: String,
    val type: String,
    val arrowSet: String,
    val arrows: List<ArrowDto>,
    val total: Int,
    val xCount: Int,
    val tenCount: Int,
)

@Serializable
data class BestMarkersDto(val practice: RoundPointDto?, val competition: RoundPointDto?)

@Serializable
data class GapViewDto(
    val practiceAverage: Double?,
    val competitionAverage: Double?,
    val gap: Double?,
    val gapOverTime: List<SeriesPointDto>,
    val arrowSetMismatch: Boolean,
    val insufficient: String?,
)

@Serializable
data class TrendViewDto(
    val practice: List<RoundPointDto>,
    val competition: List<RoundPointDto>,
    val practiceRollingAverage: List<SeriesPointDto>,
    val competitionRollingAverage: List<SeriesPointDto>,
    val bestEver: BestMarkersDto,
    val bestLast12Months: BestMarkersDto,
    val insufficient: String?,
)

@Serializable
data class DistributionBucketDto(val value: Int, val count: Int)

@Serializable
data class ConsistencyViewDto(
    val distribution: List<DistributionBucketDto>,
    val xRate: Double,
    val tenPlusXRate: Double,
    val averageArrowValue: Double,
    val standardDeviationOverTime: List<SeriesPointDto>,
    val insufficient: String?,
)

@Serializable
data class PositionAverageDto(val position: Int, val average: Double)

@Serializable
data class PatternsViewDto(
    val byEndPosition: List<PositionAverageDto>,
    val byRoundPosition: List<PositionAverageDto>,
    val insufficient: String?,
)

@Serializable
data class StatsResponseDto(
    val roundCount: Int,
    val gap: GapViewDto,
    val trend: TrendViewDto,
    val consistency: ConsistencyViewDto,
    val patterns: PatternsViewDto,
)

@Serializable
data class SyncRequestDto(val sessions: List<SessionDto>, val rounds: List<RoundDto>)

@Serializable
data class SyncResponseDto(val sessions: Int, val rounds: Int)
