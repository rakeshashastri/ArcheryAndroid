package com.archery.tracker.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@Serializable
private data class ConformanceExpected(
    val total: Int,
    val xCount: Int,
    val tenCount: Int,
    val endTotals: List<Int>,
    val complete: Boolean,
)

@Serializable
private data class ConformanceCase(
    val name: String,
    val arrows: List<JsonArray>,
    val expected: ConformanceExpected,
)

@Serializable
private data class ConformanceFixture(
    val description: String,
    val cases: List<ConformanceCase>,
)

class ConformanceTest {

    private fun decode(pairs: List<JsonArray>): List<Arrow> = pairs.map { pair ->
        Arrow(
            value = pair[0].jsonPrimitive.int,
            isX = pair[1].jsonPrimitive.boolean,
        )
    }

    @Test
    fun `contains cases`() {
        val fixture = loadFixture()
        assertTrue(fixture.cases.isNotEmpty())
    }

    @Test
    fun `every fixture case matches the Kotlin scoring implementation`() {
        val fixture = loadFixture()
        for (case in fixture.cases) {
            val arrows = decode(case.arrows)
            assertEquals("${case.name}: total", case.expected.total, roundTotal(arrows))
            assertEquals("${case.name}: xCount", case.expected.xCount, xCount(arrows))
            assertEquals("${case.name}: tenCount", case.expected.tenCount, tenCount(arrows))
            assertEquals("${case.name}: endTotals", case.expected.endTotals, endTotals(arrows))
            assertEquals("${case.name}: complete", case.expected.complete, isRoundComplete(arrows))
        }
    }

    private fun loadFixture(): ConformanceFixture {
        val file = File("../fixtures/scoring-conformance.json")
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(file.readText())
    }
}
