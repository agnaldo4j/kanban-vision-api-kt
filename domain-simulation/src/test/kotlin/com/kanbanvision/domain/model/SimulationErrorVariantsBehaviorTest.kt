package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.simulation.SimulationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SimulationErrorVariantsBehaviorTest {
    @Test
    fun `given each simulation error variant when created then payload is preserved`() {
        val simulation = SimulationError.SimulationNotFound(id = "sim-1")
        val invalidDecision = SimulationError.InvalidDecision(reason = "invalid")
        val snapshot = SimulationError.SnapshotNotFound(simulationId = "sim-1", day = 2)

        assertIs<SimulationError.SimulationNotFound>(simulation)
        assertEquals("sim-1", simulation.id)
        assertEquals("invalid", invalidDecision.reason)
        assertEquals("sim-1", snapshot.simulationId)
        assertEquals(2, snapshot.day)
    }
}
