package com.kanbanvision.usecases.scenario

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.tenant.Tenant
import com.kanbanvision.domain.model.valueobjects.TenantId
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.repositories.TenantRepository
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
    private val tenantRepository = mockk<TenantRepository>()
    private val scenarioRepository = mockk<ScenarioRepository>()
    private val useCase = CreateScenarioUseCase(tenantRepository, scenarioRepository)

    private val tenantId = TenantId("tenant-abc")
    private val tenant = Tenant(id = tenantId, name = "Test Org")
    private val command = CreateScenarioCommand(tenantId = tenantId.value, wipLimit = 3, teamSize = 2, seedValue = 42L)

    @Test
    fun `execute creates scenario and returns its id`() =
        runTest {
            coEvery { tenantRepository.findById(tenantId) } returns tenant.right()
            coEvery { scenarioRepository.save(any()) } answers { firstArg<Scenario>().right() }
            coEvery { scenarioRepository.saveState(any(), any()) } answers { secondArg<SimulationState>().right() }

            val result = useCase.execute(command)

            assertTrue(result.isRight())
            assertNotNull(result.getOrNull())
            coVerify(exactly = 1) { scenarioRepository.save(any()) }
            coVerify(exactly = 1) { scenarioRepository.saveState(any(), any()) }
        }

    @Test
    fun `execute returns TenantNotFound when tenant does not exist`() =
        runTest {
            coEvery { tenantRepository.findById(tenantId) } returns DomainError.TenantNotFound(tenantId.value).left()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.TenantNotFound>(result.leftOrNull())
            coVerify(exactly = 0) { scenarioRepository.save(any()) }
        }

    @Test
    fun `execute returns ValidationError without calling repositories when tenantId is blank`() =
        runTest {
            val result = useCase.execute(command.copy(tenantId = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { tenantRepository.findById(any()) }
        }

    @Test
    fun `execute returns PersistenceError when save fails`() =
        runTest {
            coEvery { tenantRepository.findById(tenantId) } returns tenant.right()
            coEvery { scenarioRepository.save(any()) } returns DomainError.PersistenceError("DB error").left()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
