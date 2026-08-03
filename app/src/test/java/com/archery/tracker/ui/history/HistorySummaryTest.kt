package com.archery.tracker.ui.history

import com.archery.tracker.core.Arrow
import com.archery.tracker.core.ArrowValue
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.SessionWithRounds
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySummaryTest {

    private fun fill(value: ArrowValue, isX: Boolean, count: Int): List<Arrow> = List(count) { Arrow(value, isX) }

    private fun sessionWithRounds(roundsArrows: List<List<Arrow>>) = SessionWithRounds(
        Session("s1", "2026-01-01", SessionType.PRACTICE, TimeOfDay.MORNING, "ACC", 50.0, null, ""),
        roundsArrows.mapIndexed { i, arrows ->
            Round("r$i", "s1", i + 1, TargetPosition.A, arrows, null, "")
        },
    )

    @Test
    fun `totals every round in the session`() {
        val summary = summarise(sessionWithRounds(listOf(fill(9, false, 36), fill(10, true, 36))))
        assertEquals(324 + 360, summary.total)
        assertEquals(2, summary.roundCount)
    }

    @Test
    fun `counts Xs across the session`() {
        val summary = summarise(sessionWithRounds(listOf(fill(10, true, 36))))
        assertEquals(36, summary.xCount)
    }

    @Test
    fun `flags a session containing an incomplete round`() {
        val summary = summarise(sessionWithRounds(listOf(fill(9, false, 36), fill(9, false, 12))))
        assertTrue(summary.hasIncompleteRound)
    }
}
