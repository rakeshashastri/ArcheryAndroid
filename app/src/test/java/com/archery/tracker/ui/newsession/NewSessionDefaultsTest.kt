package com.archery.tracker.ui.newsession

import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.SessionWithRounds
import com.archery.tracker.core.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Test

class NewSessionDefaultsTest {

    private fun sessionWithRounds(
        id: String, date: String, type: SessionType, arrowSet: String, poundage: Double,
    ) = SessionWithRounds(
        Session(id, date, type, TimeOfDay.MORNING, arrowSet, poundage, null, ""),
        emptyList(),
    )

    @Test
    fun `falls back when there is no history`() {
        assertEquals(FALLBACK_DEFAULTS, deriveDefaults(emptyList(), SessionType.PRACTICE))
    }

    @Test
    fun `takes the arrow set from the most recent session of the same type`() {
        val sessions = listOf(
            sessionWithRounds("s1", "2026-01-01", SessionType.PRACTICE, "ACC", 50.0),
            sessionWithRounds("s2", "2026-02-01", SessionType.COMPETITION, "Easton X10", 50.0),
        )
        assertEquals("Easton X10", deriveDefaults(sessions, SessionType.COMPETITION).arrowSet)
        assertEquals("ACC", deriveDefaults(sessions, SessionType.PRACTICE).arrowSet)
    }

    @Test
    fun `takes poundage from the most recent session of any type`() {
        val sessions = listOf(
            sessionWithRounds("s1", "2026-01-01", SessionType.PRACTICE, "ACC", 50.0),
            sessionWithRounds("s2", "2026-02-01", SessionType.COMPETITION, "Easton X10", 52.0),
        )
        assertEquals(52.0, deriveDefaults(sessions, SessionType.PRACTICE).poundage, 0.0)
    }
}
