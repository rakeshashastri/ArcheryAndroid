package com.archery.tracker.data.repository

import com.archery.tracker.core.Arrow
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.local.RoundEntity
import com.archery.tracker.data.local.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MappersTest {

    @Test
    fun `session entity to domain and back preserves every field`() {
        val entity = SessionEntity(
            id = "s1", date = "2026-08-01", type = "competition", timeOfDay = "evening",
            arrowSet = "Easton X10", poundage = 52.0, notes = "windy",
            updatedAt = "2026-08-01T00:00:00Z", dirty = true,
        )
        val domain = entity.toDomain()
        assertEquals(SessionType.COMPETITION, domain.type)
        assertEquals(TimeOfDay.EVENING, domain.timeOfDay)

        val backToEntity = domain.toEntity(dirty = true)
        assertEquals(entity, backToEntity)
    }

    @Test
    fun `round entity to domain and back preserves arrows and target position`() {
        val entity = RoundEntity(
            id = "r1", sessionId = "s1", index = 2, targetPosition = "C",
            arrows = listOf(Arrow(10, true), Arrow(0, false)),
            notes = null, updatedAt = "2026-08-01T00:00:00Z", dirty = false,
        )
        val domain = entity.toDomain()
        assertEquals(TargetPosition.C, domain.targetPosition)
        assertEquals(2, domain.arrows.size)

        assertEquals(entity, domain.toEntity(dirty = false))
    }

    @Test
    fun `session toDto omits nothing the backend needs and uses a placeholder userId`() {
        val session = Session(
            id = "s1", date = "2026-08-01", type = SessionType.PRACTICE,
            timeOfDay = TimeOfDay.MORNING, arrowSet = "ACC", poundage = 50.0,
            notes = null, updatedAt = "2026-08-01T00:00:00Z",
        )
        val dto = session.toDto()
        assertEquals("s1", dto.id)
        assertEquals("practice", dto.type)
        assertEquals("morning", dto.timeOfDay)
        assertEquals("", dto.userId) // the backend ignores/overwrites this; there is no client identity
    }

    @Test
    fun `sessionDto toEntity marks the row not dirty by default since it came from the server`() {
        val dto = com.archery.tracker.data.remote.SessionDto(
            id = "s1", userId = "archer", date = "2026-08-01", type = "practice",
            timeOfDay = "morning", arrowSet = "ACC", poundage = 50.0,
            notes = null, updatedAt = "2026-08-01T00:00:00Z",
        )
        assertFalse(dto.toEntity().dirty)
    }
}
