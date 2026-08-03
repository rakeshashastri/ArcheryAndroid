package com.archery.tracker.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SessionDto round-trips through encode and decode`() {
        val original = SessionDto(
            id = "s1", userId = "archer", date = "2026-08-01", type = "practice",
            timeOfDay = "morning", arrowSet = "ACC", poundage = 50.0,
            notes = null, updatedAt = "2026-08-01T00:00:00Z",
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SessionDto>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `RoundDto round-trips arrows through encode and decode`() {
        val original = RoundDto(
            id = "r1", sessionId = "s1", index = 1, targetPosition = "A",
            arrows = listOf(ArrowDto(value = 10, isX = true), ArrowDto(value = 9, isX = false)),
            notes = null, updatedAt = "2026-08-01T00:00:00Z",
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<RoundDto>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `StatsResponseDto decodes a realistic empty-data payload from the live backend`() {
        val body = """
            {"roundCount":0,"gap":{"practiceAverage":null,"competitionAverage":null,"gap":null,
            "gapOverTime":[],"arrowSetMismatch":false,"insufficient":"Needs at least 1 complete competition round and 3 complete practice rounds."},
            "trend":{"practice":[],"competition":[],"practiceRollingAverage":[],"competitionRollingAverage":[],
            "bestEver":{"practice":null,"competition":null},"bestLast12Months":{"practice":null,"competition":null},
            "insufficient":"Needs at least 3 complete rounds."},
            "consistency":{"distribution":[{"value":10,"count":0},{"value":9,"count":0},{"value":8,"count":0},
            {"value":7,"count":0},{"value":6,"count":0},{"value":5,"count":0},{"value":0,"count":0}],
            "xRate":0,"tenPlusXRate":0,"averageArrowValue":0,"standardDeviationOverTime":[],
            "insufficient":"Needs at least 3 complete rounds."},
            "patterns":{"byEndPosition":[],"byRoundPosition":[],"insufficient":"Needs at least 3 complete rounds."}}
        """.trimIndent()

        val stats = json.decodeFromString<StatsResponseDto>(body)
        assertEquals(0, stats.roundCount)
        assertNull(stats.gap.gap)
        assertEquals(7, stats.consistency.distribution.size)
        assertEquals("Needs at least 3 complete rounds.", stats.trend.insufficient)
    }
}
