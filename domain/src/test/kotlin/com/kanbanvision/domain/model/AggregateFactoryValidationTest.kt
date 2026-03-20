package com.kanbanvision.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AggregateFactoryValidationTest {
    @Test
    fun `given audit timestamps when factory and guards are used then invariants hold`() {
        val now = Instant.parse("2026-03-20T00:00:00Z")
        val audit = Audit.now(now)

        assertEquals(now, audit.createdAt)
        assertEquals(now, audit.updatedAt)
        assertNotNull(audit.touch(now.plusSeconds(60)).updatedAt)

        assertFailsWith<IllegalArgumentException> { Audit(createdAt = now, updatedAt = now.minusSeconds(1)) }
        assertFailsWith<IllegalArgumentException> { Audit(createdAt = now, deletedAt = now.minusSeconds(1)) }
    }

    @Test
    fun `given aggregate factories when invalid input is provided then construction fails`() {
        assertFailsWith<IllegalArgumentException> { Organization.create("") }
        assertFailsWith<IllegalArgumentException> { Board.create("") }
        assertFailsWith<IllegalArgumentException> { Scenario.create("", ScenarioRules.create(1, 1, 1L)) }
        assertFailsWith<IllegalArgumentException> {
            Simulation.create("", Organization.create("Org"), Scenario.create("S", ScenarioRules.create(1, 1, 1L)))
        }
        assertFailsWith<IllegalArgumentException> { PolicySet(wipLimit = 0) }
        assertFailsWith<IllegalArgumentException> {
            ScenarioRules(policySet = PolicySet(wipLimit = 2), wipLimit = 1, teamSize = 1, seedValue = 1L)
        }
    }

    @Test
    fun `given snapshot movement and metrics when ids or values are invalid then validation fails`() {
        val metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
        val movement = Movement(type = MovementType.MOVED, cardId = "c-1", day = SimulationDay(1), reason = "ok")
        val snapshot =
            DailySnapshot(
                simulationId = "sim-1",
                day = SimulationDay(1),
                metrics = metrics,
                movements = listOf(movement),
            )

        assertEquals("sim-1", snapshot.simulationId)
        assertEquals(1, snapshot.movements.size)

        assertFailsWith<IllegalArgumentException> { FlowMetrics(throughput = -1, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0) }
        assertFailsWith<IllegalArgumentException> { FlowMetrics(throughput = 0, wipCount = -1, blockedCount = 0, avgAgingDays = 0.0) }
        assertFailsWith<IllegalArgumentException> { FlowMetrics(throughput = 0, wipCount = 0, blockedCount = -1, avgAgingDays = 0.0) }
        assertFailsWith<IllegalArgumentException> { FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = -0.1) }
        assertFailsWith<IllegalArgumentException> { Movement(type = MovementType.MOVED, cardId = "", day = SimulationDay(1), reason = "x") }
        assertFailsWith<IllegalArgumentException> {
            DailySnapshot(simulationId = "", day = SimulationDay(1), metrics = metrics, movements = emptyList())
        }
    }
}
