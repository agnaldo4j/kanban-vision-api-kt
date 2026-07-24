package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.simulation.SimulationError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SimulationErrorGuardTest {
    @Test
    fun `given non positive day when creating day already executed then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            SimulationError.DayAlreadyExecuted(0)
        }
    }

    @Test
    fun `given non positive day when creating snapshot not found then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            SimulationError.SnapshotNotFound("sim-1", 0)
        }
    }
}
