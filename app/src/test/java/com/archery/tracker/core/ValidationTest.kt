package com.archery.tracker.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    private fun round(
        index: Int = 1,
        arrows: List<Arrow> = emptyList(),
    ) = Round(
        id = "r1", sessionId = "s1", index = index, targetPosition = TargetPosition.A,
        arrows = arrows, notes = null, updatedAt = "2026-08-01T00:00:00Z",
    )

    private fun session(type: SessionType = SessionType.PRACTICE) = Session(
        id = "s1", date = "2026-08-01", type = type, timeOfDay = TimeOfDay.MORNING,
        arrowSet = "Easton X10", poundage = 50.0, notes = null,
        updatedAt = "2026-08-01T00:00:00Z",
    )

    private fun codes(errors: List<ValidationError>): List<String> = errors.map { it.code }

    @Test
    fun `validateArrow accepts a legal value`() {
        assertTrue(validateArrow(Arrow(9, false)).isEmpty())
    }

    @Test
    fun `validateArrow rejects a value outside the scoring zones`() {
        assertTrue(codes(validateArrow(Arrow(4, false))).contains("ARROW_INVALID_VALUE"))
    }

    @Test
    fun `validateArrow rejects isX on anything but a ten`() {
        assertTrue(codes(validateArrow(Arrow(9, true))).contains("ARROW_X_ON_NON_TEN"))
    }

    @Test
    fun `validateArrow accepts isX on a ten`() {
        assertTrue(validateArrow(Arrow(10, true)).isEmpty())
    }

    @Test
    fun `validateRound accepts an empty in-progress round`() {
        assertTrue(validateRound(round(), SessionType.PRACTICE).isEmpty())
    }

    @Test
    fun `validateRound rejects a 37th arrow`() {
        val arrows = List(37) { Arrow(9, false) }
        assertTrue(codes(validateRound(round(arrows = arrows), SessionType.PRACTICE)).contains("ROUND_TOO_MANY_ARROWS"))
    }

    @Test
    fun `validateRound rejects a fifth round in a practice session`() {
        assertTrue(codes(validateRound(round(index = 5), SessionType.PRACTICE)).contains("ROUND_INDEX_OUT_OF_RANGE"))
    }

    @Test
    fun `validateRound rejects a third round in a competition session`() {
        assertTrue(codes(validateRound(round(index = 3), SessionType.COMPETITION)).contains("ROUND_INDEX_OUT_OF_RANGE"))
    }

    @Test
    fun `validateRound surfaces invalid arrows from within the round`() {
        val errors = validateRound(round(arrows = listOf(Arrow(4, false))), SessionType.PRACTICE)
        assertTrue(codes(errors).contains("ARROW_INVALID_VALUE"))
    }

    @Test
    fun `validateSession rejects a session with no rounds`() {
        assertTrue(codes(validateSession(session(), emptyList())).contains("SESSION_NO_ROUNDS"))
    }

    @Test
    fun `validateSession accepts a practice session with four rounds`() {
        val rounds = (1..4).map { round(index = it) }
        assertTrue(validateSession(session(), rounds).isEmpty())
    }

    @Test
    fun `validateSession accepts an incomplete competition session with one round`() {
        val errors = validateSession(session(SessionType.COMPETITION), listOf(round(index = 1)))
        assertTrue(codes(errors).none { it == "SESSION_ROUND_COUNT" })
    }

    @Test
    fun `validateSession rejects a competition session with more than two rounds`() {
        val rounds = listOf(round(index = 1), round(index = 2), round(index = 3))
        val errors = validateSession(session(SessionType.COMPETITION), rounds)
        assertTrue(codes(errors).contains("SESSION_ROUND_COUNT"))
    }

    @Test
    fun `validateSession rejects duplicate round indexes`() {
        val rounds = listOf(round(index = 1), round(index = 1))
        assertTrue(codes(validateSession(session(), rounds)).contains("SESSION_DUPLICATE_ROUND_INDEX"))
    }
}
