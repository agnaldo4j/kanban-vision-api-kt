package com.kanbanvision.usecases.simulation

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.usecases.repositories.OrganizationRepository
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.simulation.commands.CreateSimulationCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateSimulationUseCaseTest {
    private val organizationRepository = mockk<OrganizationRepository>()
    private val simulationRepository = mockk<SimulationRepository>()
    private val useCase = CreateSimulationUseCase(organizationRepository, simulationRepository)

    @Test
    fun `given valid command when organization exists then use case creates and persists simulation`() =
        runTest {
            val organization = fixtureOrganization(id = "org-42", name = "Kanban Vision")
            val simulationSlot = slot<Simulation>()

            coEvery { organizationRepository.findById("org-42") } returns organization.right()
            coEvery { simulationRepository.save(capture(simulationSlot)) } answers { simulationSlot.captured.right() }

            val result =
                useCase.execute(
                    CreateSimulationCommand(
                        organizationId = "org-42",
                        wipLimit = 3,
                        teamSize = 4,
                        seedValue = 99L,
                    ),
                )

            assertTrue(result.isRight())
            val simulationId = result.getOrElse { error("Expected simulation id") }
            assertEquals(simulationSlot.captured.id, simulationId)
            assertEquals("Default Simulation Scenario", simulationSlot.captured.scenario.name)
            assertEquals(3, simulationSlot.captured.scenario.rules.wipLimit)
            assertEquals(4, simulationSlot.captured.scenario.rules.teamSize)

            coVerify(exactly = 1) { organizationRepository.findById("org-42") }
            coVerify(exactly = 1) { simulationRepository.save(any()) }
        }

    @Test
    fun `given invalid command when simulation creation is requested then validation error is returned`() =
        runTest {
            val result =
                useCase.execute(
                    CreateSimulationCommand(
                        organizationId = "",
                        wipLimit = 0,
                        teamSize = 0,
                        seedValue = 1L,
                    ),
                )

            assertTrue(result.isLeft())
            val error = result.leftOrNull()
            assertIs<DomainError.ValidationError>(error)

            coVerify(exactly = 0) { organizationRepository.findById(any()) }
            coVerify(exactly = 0) { simulationRepository.save(any()) }
        }

    @Test
    fun `given missing organization when simulation creation is requested then not found error is propagated`() =
        runTest {
            coEvery { organizationRepository.findById("org-missing") } returns DomainError.OrganizationNotFound("org-missing").left()

            val result =
                useCase.execute(
                    CreateSimulationCommand(
                        organizationId = "org-missing",
                        wipLimit = 2,
                        teamSize = 2,
                        seedValue = 10L,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.OrganizationNotFound>(result.leftOrNull())

            coVerify(exactly = 1) { organizationRepository.findById("org-missing") }
            coVerify(exactly = 0) { simulationRepository.save(any()) }
        }
}
