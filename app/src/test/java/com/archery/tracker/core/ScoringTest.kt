package com.archery.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {

    private fun repeat(value: ArrowValue, isX: Boolean, n: Int): List<Arrow> =
        List(n) { Arrow(value, isX) }

    @Test
    fun `ends slices arrows into groups of six`() {
        assertEquals(2, ends(repeat(9, false, 12)).size)
    }

    @Test
    fun `ends keeps a trailing partial end`() {
        val result = ends(repeat(9, false, 8))
        assertEquals(2, result.size)
        assertEquals(2, result[1].size)
    }

    @Test
    fun `ends returns nothing for no arrows`() {
        assertTrue(ends(emptyList()).isEmpty())
    }

    @Test
    fun `roundTotal sums a perfect round to 360`() {
        assertEquals(360, roundTotal(repeat(10, true, 36)))
    }

    @Test
    fun `roundTotal scores an all-miss round as 0`() {
        assertEquals(0, roundTotal(repeat(0, false, 36)))
    }

    @Test
    fun `roundTotal counts an X as ten not eleven`() {
        val arrows = listOf(Arrow(10, true), Arrow(10, false))
        assertEquals(20, roundTotal(arrows))
    }

    @Test
    fun `endTotals returns one total per end`() {
        val arrows = repeat(10, false, 6) + repeat(9, false, 6)
        assertEquals(listOf(60, 54), endTotals(arrows))
    }

    @Test
    fun `runningTotals accumulates end totals`() {
        val arrows = repeat(10, false, 6) + repeat(9, false, 6)
        assertEquals(listOf(60, 114), runningTotals(arrows))
    }

    @Test
    fun `counts Xs separately from tens`() {
        val arrows = listOf(Arrow(10, true), Arrow(10, true), Arrow(10, false), Arrow(9, false))
        assertEquals(2, xCount(arrows))
        assertEquals(3, tenCount(arrows))
    }

    @Test
    fun `isRoundComplete is true at exactly 36 arrows`() {
        assertTrue(isRoundComplete(repeat(9, false, 36)))
    }

    @Test
    fun `isRoundComplete is false below 36 arrows`() {
        assertFalse(isRoundComplete(repeat(9, false, 35)))
    }
}
