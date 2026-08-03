package com.archery.tracker.data.repository

import com.archery.tracker.core.Arrow
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.RoundEntity
import com.archery.tracker.data.local.SessionEntity
import com.archery.tracker.data.remote.ArrowDto
import com.archery.tracker.data.remote.RoundDto
import com.archery.tracker.data.remote.SessionDto

private fun SessionType.wire(): String = when (this) {
    SessionType.PRACTICE -> "practice"
    SessionType.COMPETITION -> "competition"
}

private fun sessionTypeFromWire(value: String): SessionType = when (value) {
    "competition" -> SessionType.COMPETITION
    else -> SessionType.PRACTICE
}

private fun TimeOfDay.wire(): String = when (this) {
    TimeOfDay.MORNING -> "morning"
    TimeOfDay.EVENING -> "evening"
}

private fun timeOfDayFromWire(value: String): TimeOfDay = when (value) {
    "evening" -> TimeOfDay.EVENING
    else -> TimeOfDay.MORNING
}

private fun TargetPosition.wire(): String = name

private fun targetPositionFromWire(value: String): TargetPosition =
    TargetPosition.entries.firstOrNull { it.name == value } ?: TargetPosition.A

fun SessionEntity.toDomain(): Session = Session(
    id = id, date = date, type = sessionTypeFromWire(type), timeOfDay = timeOfDayFromWire(timeOfDay),
    arrowSet = arrowSet, poundage = poundage, notes = notes, updatedAt = updatedAt,
)

fun Session.toEntity(dirty: Boolean): SessionEntity = SessionEntity(
    id = id, date = date, type = type.wire(), timeOfDay = timeOfDay.wire(),
    arrowSet = arrowSet, poundage = poundage, notes = notes, updatedAt = updatedAt, dirty = dirty,
)

fun RoundEntity.toDomain(): Round = Round(
    id = id, sessionId = sessionId, index = index, targetPosition = targetPositionFromWire(targetPosition),
    arrows = arrows, notes = notes, updatedAt = updatedAt,
)

fun Round.toEntity(dirty: Boolean): RoundEntity = RoundEntity(
    id = id, sessionId = sessionId, index = index, targetPosition = targetPosition.wire(),
    arrows = arrows, notes = notes, updatedAt = updatedAt, dirty = dirty,
)

fun Session.toDto(): SessionDto = SessionDto(
    id = id, userId = "", date = date, type = type.wire(), timeOfDay = timeOfDay.wire(),
    arrowSet = arrowSet, poundage = poundage, notes = notes, updatedAt = updatedAt,
)

fun SessionDto.toEntity(dirty: Boolean = false): SessionEntity = SessionEntity(
    id = id, date = date, type = type, timeOfDay = timeOfDay,
    arrowSet = arrowSet, poundage = poundage, notes = notes, updatedAt = updatedAt, dirty = dirty,
)

fun Round.toDto(): RoundDto = RoundDto(
    id = id, sessionId = sessionId, index = index, targetPosition = targetPosition.wire(),
    arrows = arrows.map { ArrowDto(it.value, it.isX) }, notes = notes, updatedAt = updatedAt,
)

fun RoundDto.toEntity(dirty: Boolean = false): RoundEntity = RoundEntity(
    id = id, sessionId = sessionId, index = index, targetPosition = targetPosition,
    arrows = arrows.map { Arrow(it.value, it.isX) }, notes = notes, updatedAt = updatedAt, dirty = dirty,
)
