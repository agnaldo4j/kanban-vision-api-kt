package com.kanbanvision.domain.errors

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DomainErrorTest {
    @Test
    fun `given non positive day when creating day already executed then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            SimulationError.DayAlreadyExecuted(0)
        }
    }
}
