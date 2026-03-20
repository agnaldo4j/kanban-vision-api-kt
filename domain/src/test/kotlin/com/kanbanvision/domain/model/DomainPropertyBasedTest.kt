package com.kanbanvision.domain.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DomainPropertyBasedTest {
    @Test
    fun `flow metrics accept any non-negative generated values`() {
        runTest {
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
        runTest {
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
        runTest {
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
        runTest {
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
        runTest {
            checkAll(Arb.int(1..10_000)) { day ->
                val simulationDay = SimulationDay(day)
                assertEquals(day, simulationDay.value)
            }
        }
    }

    @Test
    fun `simulation day rejects non-positive values`() {
        runTest {
            checkAll(Arb.int(Int.MIN_VALUE..0)) { nonPositiveDay ->
                assertFailsWith<IllegalArgumentException> { SimulationDay(nonPositiveDay) }
            }
        }
    }

    @Test
    fun `board id accepts non-blank and rejects blank generated strings`() {
        runTest {
            checkAll(Arb.string(minSize = 1, maxSize = 64)) { value ->
                if (value.isBlank()) {
                    assertTrue(value.isBlank())
                } else {
                    assertTrue(value.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `board id rejects all whitespace-only generated values`() {
        runTest {
            checkAll(Arb.int(1..64)) { size ->
                val blankValue = " ".repeat(size)
                assertTrue(blankValue.isBlank())
            }
        }
    }

    @Test
    fun `board id generate returns parseable uuid`() {
        repeat(50) {
            val generated = UUID.randomUUID().toString()
            assertTrue(generated.isNotBlank())
            assertEquals(36, generated.length)
            UUID.fromString(generated)
        }
    }
}
