package com.archery.tracker.core

import kotlinx.serialization.Serializable

typealias ArrowValue = Int

@Serializable
data class Arrow(
    val value: ArrowValue,
    val isX: Boolean,
)

enum class SessionType { PRACTICE, COMPETITION }
enum class TimeOfDay { MORNING, EVENING }
enum class TargetPosition { A, B, C, D }

data class Round(
    val id: String,
    val sessionId: String,
    val index: Int,
    val targetPosition: TargetPosition,
    val arrows: List<Arrow>,
    val notes: String?,
    val updatedAt: String,
)

data class Session(
    val id: String,
    val date: String,
    val type: SessionType,
    val timeOfDay: TimeOfDay,
    val arrowSet: String,
    val poundage: Double,
    val notes: String?,
    val updatedAt: String,
)

data class SessionWithRounds(
    val session: Session,
    val rounds: List<Round>,
)

const val ARROWS_PER_END = 6
const val ENDS_PER_ROUND = 6
const val ARROWS_PER_ROUND = 36
const val MAX_ROUND_SCORE = 360

val VALID_ARROW_VALUES: List<ArrowValue> = listOf(0, 5, 6, 7, 8, 9, 10)

val ROUNDS_PER_SESSION: Map<SessionType, Int> = mapOf(
    SessionType.PRACTICE to 4,
    SessionType.COMPETITION to 2,
)
