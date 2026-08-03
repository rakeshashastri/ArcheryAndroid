package com.archery.tracker.ui.sessiondetail

import com.archery.tracker.core.Arrow
import org.junit.Assert.assertEquals
import org.junit.Test

class ScorecardTest {

    @Test
    fun `arrowLabel marks X, miss, and plain values`() {
        assertEquals("X", arrowLabel(Arrow(10, true)))
        assertEquals("M", arrowLabel(Arrow(0, false)))
        assertEquals("9", arrowLabel(Arrow(9, false)))
    }

    @Test
    fun `descendingEnd sorts by value descending with X above a plain ten`() {
        val end = listOf(
            Arrow(8, false), Arrow(10, false), Arrow(9, false),
            Arrow(0, false), Arrow(10, true), Arrow(6, false),
        )
        val sorted = descendingEnd(end).map { arrowLabel(it) }
        assertEquals(listOf("X", "10", "9", "8", "6", "M"), sorted)
    }
}
