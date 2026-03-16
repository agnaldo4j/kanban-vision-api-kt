package com.kanbanvision.usecases.scenario

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.ScenarioConfig
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.valueobjects.TenantId
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.scenario.queries.GetScenarioQuery
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetScenarioUseCaseTest {
    private val scenarioRepository = mockk<ScenarioRepository>()
    private val useCase = GetScenarioUseCase(scenarioRepository)

    private val scenarioId = ScenarioId("scenario-1")
    private val config = ScenarioConfig(wipLimit = 2, teamSize = 3, seedValue = 42L)
    private val scenario = Scenario(id = scenarioId, tenantId = TenantId("t-1"), config = config)
    private val state = SimulationState.initial(config)

    @Test
    fun `execute returns scenario with state`() =
        runTest {
            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns state.right()

            val result = useCase.execute(GetScenarioQuery(scenarioId = scenarioId.value))

            assertTrue(result.isRight())
            val found = result.getOrNull()
            assertNotNull(found)
            assertTrue(found.scenario.id == scenarioId)
        }

    @Test
    fun `execute returns ScenarioNotFound when scenario does not exist`() =
        runTest {
            coEvery { scenarioRepository.findById(scenarioId) } returns DomainError.ScenarioNotFound(scenarioId.value).left()

            val result = useCase.execute(GetScenarioQuery(scenarioId = scenarioId.value))

            assertTrue(result.isLeft())
            assertIs<DomainError.ScenarioNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns ValidationError when scenarioId is blank`() =
        runTest {
            val result = useCase.execute(GetScenarioQuery(scenarioId = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }
}
