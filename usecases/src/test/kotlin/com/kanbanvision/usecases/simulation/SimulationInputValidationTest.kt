package com.kanbanvision.usecases.simulation

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.simulation.commands.CreateSimulationCommand
import com.kanbanvision.usecases.simulation.commands.RunDayCommand
import com.kanbanvision.usecases.simulation.queries.GetDailySnapshotQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationCfdQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationDaysQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationQuery
import com.kanbanvision.usecases.simulation.queries.ListSimulationsQuery
import kotlin.test.Test
import kotlin.test.assertContains
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
        assertTrue(error.messages.size >= 3)
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

    @Test
    fun `given list simulations query with multiple invalid fields when validating then all messages are accumulated`() {
        val result = ListSimulationsQuery(organizationId = "", page = 0, size = 0).validate()

        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertContains(error.messages, "Organization id must not be blank")
        assertContains(error.messages, "Page must be at least 1")
        assertContains(error.messages, "Size must be between 1 and 100")
    }

    @Test
    fun `given list simulations query with size above max when validating then validation error is returned`() {
        val result = ListSimulationsQuery(organizationId = "org-1", size = 101).validate()

        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `given get simulation days query with blank id when validating then validation error is returned`() {
        val result = GetSimulationDaysQuery(simulationId = "").validate()

        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `given get simulation cfd query with blank id when validating then validation error is returned`() {
        val result = GetSimulationCfdQuery(simulationId = "").validate()

        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
