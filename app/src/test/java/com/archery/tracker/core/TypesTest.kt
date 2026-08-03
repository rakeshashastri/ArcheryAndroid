package com.archery.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypesTest {

    @Test
    fun `valid arrow values are exactly the seven scoring zones`() {
        assertEquals(setOf(0, 5, 6, 7, 8, 9, 10), VALID_ARROW_VALUES.toSet())
    }

    @Test
    fun `arrows per round is 36`() {
        assertEquals(36, ARROWS_PER_ROUND)
        assertEquals(6, ARROWS_PER_END)
        assertEquals(6, ENDS_PER_ROUND)
        assertEquals(360, MAX_ROUND_SCORE)
    }

    @Test
    fun `rounds per session type matches practice 4 competition 2`() {
        assertEquals(4, ROUNDS_PER_SESSION[SessionType.PRACTICE])
        assertEquals(2, ROUNDS_PER_SESSION[SessionType.COMPETITION])
    }

    @Test
    fun `an arrow with isX true and value 10 is constructible`() {
        val arrow = Arrow(value = 10, isX = true)
        assertTrue(arrow.isX)
        assertEquals(10, arrow.value)
    }

    @Test
    fun `sessionWithRounds carries a session and its rounds together`() {
        val session = Session(
            id = "s1", date = "2026-01-01", type = SessionType.PRACTICE,
            timeOfDay = TimeOfDay.MORNING, arrowSet = "ACC", poundage = 50.0,
            notes = null, updatedAt = "2026-01-01T00:00:00Z",
        )
        val round = Round(
            id = "r1", sessionId = "s1", index = 1, targetPosition = TargetPosition.A,
            arrows = emptyList(), notes = null, updatedAt = "2026-01-01T00:00:00Z",
        )
        val withRounds = SessionWithRounds(session, listOf(round))
        assertEquals("s1", withRounds.session.id)
        assertEquals(1, withRounds.rounds.size)
    }
}
