package com.kanbanvision.usecases.scenario

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.usecases.repositories.OrganizationRepository
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.scenario.commands.CreateScenarioCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateScenarioUseCaseTest {
    private val organizationRepository = mockk<OrganizationRepository>()
    private val scenarioRepository = mockk<ScenarioRepository>()
    private val useCase = CreateScenarioUseCase(organizationRepository, scenarioRepository)

    private val organizationId = "organization-abc"
    private val organization = Organization(id = organizationId, name = "Test Org")
    private val command = CreateScenarioCommand(organizationId = organizationId, wipLimit = 3, teamSize = 2, seedValue = 42L)

    @Test
    fun `execute creates scenario and returns its id`() =
        runTest {
            coEvery { organizationRepository.findById(organizationId) } returns organization.right()
            coEvery { scenarioRepository.save(any()) } answers { firstArg<Scenario>().right() }
            coEvery { scenarioRepository.saveState(any(), any()) } answers { secondArg<SimulationState>().right() }

            val result = useCase.execute(command)

            assertTrue(result.isRight())
            assertNotNull(result.getOrNull())
            coVerify(exactly = 1) { scenarioRepository.save(any()) }
            coVerify(exactly = 1) { scenarioRepository.saveState(any(), any()) }
        }

    @Test
    fun `execute returns OrganizationNotFound when organization does not exist`() =
        runTest {
            coEvery { organizationRepository.findById(organizationId) } returns DomainError.OrganizationNotFound(organizationId).left()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.OrganizationNotFound>(result.leftOrNull())
            coVerify(exactly = 0) { scenarioRepository.save(any()) }
        }

    @Test
    fun `execute returns ValidationError without calling repositories when organizationId is blank`() =
        runTest {
            val result = useCase.execute(command.copy(organizationId = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { organizationRepository.findById(any()) }
        }

    @Test
    fun `execute returns PersistenceError when save fails`() =
        runTest {
            coEvery { organizationRepository.findById(organizationId) } returns organization.right()
            coEvery { scenarioRepository.save(any()) } returns DomainError.PersistenceError("DB error").left()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
