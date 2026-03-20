package com.kanbanvision.httpapi

import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.httpapi.routes.toResponse
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioDtoPropertyBasedTest {
    @Test
    fun `daily snapshot toResponse preserves scalar fields for generated values`() {
        runTest {
            checkAll(
                Arb.int(1..1_000_000),
                Arb.int(1..10_000),
                Arb.int(0..10_000),
                Arb.int(0..10_000),
                Arb.int(0..10_000),
                Arb.double(0.0, 3650.0),
            ) { scenarioSeed, dayValue, throughput, wipCount, blockedCount, avgAgingDays ->
                val metrics =
                    FlowMetrics(
                        throughput = throughput,
                        wipCount = wipCount,
                        blockedCount = blockedCount,
                        avgAgingDays = avgAgingDays,
                    )
                val snapshot =
                    snapshotWithMetrics(
                        scenarioSeed = scenarioSeed,
                        dayValue = dayValue,
                        metrics = metrics,
                    )

                assertScalarMapping(snapshot)
            }
        }
    }

    @Test
    fun `daily snapshot toResponse preserves generated movement collections`() {
        runTest {
            checkAll(
                Arb.int(1..1_000_000),
                Arb.int(1..100),
                Arb.int(0..20),
                Arb.enum<MovementType>(),
            ) { scenarioSeed, dayValue, movementCount, movementType ->
                val snapshot =
                    snapshotWithMovements(
                        scenarioSeed = scenarioSeed,
                        dayValue = dayValue,
                        movementCount = movementCount,
                        movementType = movementType,
                    )

                assertMovementMapping(snapshot)
            }
        }
    }

    private fun snapshotWithMetrics(
        scenarioSeed: Int,
        dayValue: Int,
        metrics: FlowMetrics,
    ): DailySnapshot =
        DailySnapshot(
            scenarioId = "scenario-$scenarioSeed",
            day = SimulationDay(dayValue),
            metrics = metrics,
            movements = emptyList(),
        )

    private fun snapshotWithMovements(
        scenarioSeed: Int,
        dayValue: Int,
        movementCount: Int,
        movementType: MovementType,
    ): DailySnapshot {
        val movementTypes = MovementType.entries
        val movements =
            (0 until movementCount).map { idx ->
                Movement(
                    type = movementTypes[(movementType.ordinal + idx) % movementTypes.size],
                    cardId = "work-item-$scenarioSeed-$idx",
                    day = SimulationDay(dayValue + idx),
                    reason = "reason-$idx",
                )
            }
        return DailySnapshot(
            scenarioId = "scenario-$scenarioSeed",
            day = SimulationDay(dayValue),
            metrics =
                FlowMetrics(
                    throughput = movementCount,
                    wipCount = movementCount,
                    blockedCount = 0,
                    avgAgingDays = 0.0,
                ),
            movements = movements,
        )
    }

    private fun assertScalarMapping(snapshot: DailySnapshot) {
        val response = snapshot.toResponse()
        assertEquals(snapshot.scenarioId, response.scenarioId)
        assertEquals(snapshot.day.value, response.day)
        assertEquals(snapshot.metrics.throughput, response.metrics.throughput)
        assertEquals(snapshot.metrics.wipCount, response.metrics.wipCount)
        assertEquals(snapshot.metrics.blockedCount, response.metrics.blockedCount)
        assertEquals(snapshot.metrics.avgAgingDays, response.metrics.avgAgingDays)
    }

    private fun assertMovementMapping(snapshot: DailySnapshot) {
        val response = snapshot.toResponse()
        assertEquals(snapshot.movements.size, response.movements.size)
        response.movements.forEachIndexed { idx, movementResponse ->
            val source = snapshot.movements[idx]
            assertEquals(source.type.name, movementResponse.type)
            assertEquals(source.cardId, movementResponse.cardId)
            assertEquals(source.day.value, movementResponse.day)
            assertEquals(source.reason, movementResponse.reason)
        }
        assertTrue(response.movements.all { it.cardId.isNotBlank() })
    }
}
