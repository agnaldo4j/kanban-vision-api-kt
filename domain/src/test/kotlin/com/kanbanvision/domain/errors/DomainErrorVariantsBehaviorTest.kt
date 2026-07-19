package com.kanbanvision.domain.errors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DomainErrorVariantsBehaviorTest {
    @Test
    fun `given each simulation error variant when created then payload is preserved`() {
        val simulation = SimulationError.SimulationNotFound(id = "sim-1")
        val invalidDecision = SimulationError.InvalidDecision(reason = "invalid")

        assertIs<SimulationError.SimulationNotFound>(simulation)
        assertEquals("sim-1", simulation.id)
        assertEquals("invalid", invalidDecision.reason)
    }
}
