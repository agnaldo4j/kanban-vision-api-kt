package com.kanbanvision.usecases.simulation

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.simulation.commands.CreateSimulationCommand
import com.kanbanvision.usecases.simulation.commands.RunDayCommand
import com.kanbanvision.usecases.simulation.queries.GetDailySnapshotQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationQuery
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SimulationInputValidationTest {
    @Test
    fun `given create simulation command with invalid fields when validating then all messages are accumulated`() {
        val result =
            CreateSimulationCommand(
                organizationId = "",
                wipLimit = 0,
                teamSize = 0,
                seedValue = 0L,
            ).validate()

        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertEquals(3, error.messages.size)
        assertContains(error.messages, "Organization id must not be blank")
        assertContains(error.messages, "WIP limit must be at least 1")
        assertContains(error.messages, "Team size must be at least 1")
    }

    @Test
    fun `given run day command with blank simulation id when validating then validation error is returned`() {
        val result = RunDayCommand(simulationId = "", decisions = emptyList()).validate()

        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `given get simulation query with blank id when validating then validation error is returned`() {
        val result = GetSimulationQuery(simulationId = "").validate()

        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `given daily snapshot query with invalid inputs when validating then all messages are accumulated`() {
        val result = GetDailySnapshotQuery(simulationId = "", day = 0).validate()

        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertContains(error.messages, "Simulation id must not be blank")
        assertContains(error.messages, "Day must be at least 1")
    }
}
