package com.kanbanvision.domain.model

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailySnapshotTest {
    @Test
    fun `snapshot holds all fields`() {
        val scenarioId = UUID.randomUUID().toString()
        val day = SimulationDay(3)
        val metrics = FlowMetrics(throughput = 1, wipCount = 2, blockedCount = 0, avgAgingDays = 1.5)
        val snapshot =
            DailySnapshot(
                scenarioId = scenarioId,
                day = day,
                metrics = metrics,
                movements = emptyList(),
            )
        assertEquals(scenarioId, snapshot.scenarioId)
        assertEquals(3, snapshot.day.value)
        assertEquals(1, snapshot.metrics.throughput)
        assertTrue(snapshot.movements.isEmpty())
    }
}
