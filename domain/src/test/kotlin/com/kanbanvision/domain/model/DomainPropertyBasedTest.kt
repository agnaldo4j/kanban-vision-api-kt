package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.metrics.FlowMetrics
import com.kanbanvision.domain.model.scenario.ScenarioConfig
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.valueobjects.BoardId
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DomainPropertyBasedTest {
    @Test
    fun `flow metrics accept any non-negative generated values`() {
        runBlocking {
            checkAll(
                Arb.int(0..10_000),
                Arb.int(0..10_000),
                Arb.int(0..10_000),
                Arb.double(0.0, 3650.0),
            ) { throughput, wipCount, blockedCount, avgAgingDays ->
                val metrics = FlowMetrics(throughput, wipCount, blockedCount, avgAgingDays)
                assertEquals(throughput, metrics.throughput)
                assertEquals(wipCount, metrics.wipCount)
                assertEquals(blockedCount, metrics.blockedCount)
                assertEquals(avgAgingDays, metrics.avgAgingDays)
            }
        }
    }

    @Test
    fun `flow metrics reject any negative throughput`() {
        runBlocking {
            checkAll(
                Arb.int(Int.MIN_VALUE..-1),
                Arb.int(0..100),
                Arb.int(0..100),
                Arb.double(0.0, 100.0),
            ) { negativeThroughput, wipCount, blockedCount, avgAgingDays ->
                assertFailsWith<IllegalArgumentException> {
                    FlowMetrics(negativeThroughput, wipCount, blockedCount, avgAgingDays)
                }
            }
        }
    }

    @Test
    fun `scenario config accepts generated positive limits and team sizes`() {
        runBlocking {
            checkAll(
                Arb.int(1..10_000),
                Arb.int(1..1_000),
                Arb.long(),
            ) { wipLimit, teamSize, seedValue ->
                val config = ScenarioConfig(wipLimit, teamSize, seedValue)
                assertEquals(wipLimit, config.wipLimit)
                assertEquals(teamSize, config.teamSize)
                assertEquals(seedValue, config.seedValue)
            }
        }
    }

    @Test
    fun `scenario config rejects non-positive wip limit`() {
        runBlocking {
            checkAll(
                Arb.int(Int.MIN_VALUE..0),
                Arb.int(1..100),
                Arb.long(),
            ) { nonPositiveWipLimit, teamSize, seedValue ->
                assertFailsWith<IllegalArgumentException> {
                    ScenarioConfig(nonPositiveWipLimit, teamSize, seedValue)
                }
            }
        }
    }

    @Test
    fun `simulation day accepts all positive generated days`() {
        runBlocking {
            checkAll(Arb.int(1..10_000)) { day ->
                val simulationDay = SimulationDay(day)
                assertEquals(day, simulationDay.value)
            }
        }
    }

    @Test
    fun `simulation day rejects non-positive values`() {
        runBlocking {
            checkAll(Arb.int(Int.MIN_VALUE..0)) { nonPositiveDay ->
                assertFailsWith<IllegalArgumentException> { SimulationDay(nonPositiveDay) }
            }
        }
    }

    @Test
    fun `board id accepts all generated non-blank values`() {
        runBlocking {
            checkAll(Arb.string(minSize = 1, maxSize = 64)) { value ->
                if (value.isBlank()) {
                    assertFailsWith<IllegalArgumentException> { BoardId(value) }
                } else {
                    assertEquals(value, BoardId(value).value)
                }
            }
        }
    }

    @Test
    fun `board id rejects all whitespace-only generated values`() {
        runBlocking {
            checkAll(Arb.int(1..64)) { size ->
                val blankValue = " ".repeat(size)
                assertFailsWith<IllegalArgumentException> { BoardId(blankValue) }
            }
        }
    }

    @Test
    fun `board id generate is non-blank and uuid-like`() {
        runBlocking {
            checkAll(Arb.int(1..50)) {
                val generated = BoardId.generate().value
                assertTrue(generated.isNotBlank())
                assertTrue(generated.length >= 32)
            }
        }
    }
}
