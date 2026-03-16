package com.kanbanvision.usecases.scenario

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.DailySnapshot
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.ScenarioConfig
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.valueobjects.TenantId
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.commands.RunDayCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunDayUseCaseTest {
    private val scenarioRepository = mockk<ScenarioRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val useCase = RunDayUseCase(scenarioRepository, snapshotRepository)

    private val scenarioId = ScenarioId("scenario-1")
    private val config = ScenarioConfig(wipLimit = 2, teamSize = 3, seedValue = 42L)
    private val scenario = Scenario(id = scenarioId, tenantId = TenantId("t-1"), config = config)
    private val state = SimulationState.initial(config)
    private val command = RunDayCommand(scenarioId = scenarioId.value, decisions = emptyList())

    @Test
    fun `execute runs day and returns snapshot`() =
        runTest {
            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns state.right()
            coEvery { snapshotRepository.findByDay(scenarioId, state.currentDay) } returns null.right()
            coEvery { scenarioRepository.saveState(any(), any()) } answers { secondArg<SimulationState>().right() }
            coEvery { snapshotRepository.save(any()) } answers { firstArg<DailySnapshot>().right() }

            val result = useCase.execute(command)

            assertTrue(result.isRight())
            coVerify(exactly = 1) { snapshotRepository.save(any()) }
        }

    @Test
    fun `execute returns DayAlreadyExecuted when snapshot already exists for current day`() =
        runTest {
            val existingSnapshot = mockk<DailySnapshot>()
            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns state.right()
            coEvery { snapshotRepository.findByDay(scenarioId, state.currentDay) } returns existingSnapshot.right()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.DayAlreadyExecuted>(result.leftOrNull())
            coVerify(exactly = 0) { snapshotRepository.save(any()) }
        }

    @Test
    fun `execute returns ScenarioNotFound when scenario does not exist`() =
        runTest {
            coEvery { scenarioRepository.findById(scenarioId) } returns DomainError.ScenarioNotFound(scenarioId.value).left()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ScenarioNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns ValidationError without hitting repositories when scenarioId is blank`() =
        runTest {
            val result = useCase.execute(command.copy(scenarioId = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { scenarioRepository.findById(any()) }
        }

    @Test
    fun `execute advances currentDay after running`() =
        runTest {
            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns state.right()
            coEvery { snapshotRepository.findByDay(scenarioId, state.currentDay) } returns null.right()
            coEvery { scenarioRepository.saveState(any(), any()) } answers { secondArg<SimulationState>().right() }
            coEvery { snapshotRepository.save(any()) } answers { firstArg<DailySnapshot>().right() }

            useCase.execute(command)

            coVerify {
                scenarioRepository.saveState(
                    scenarioId,
                    match { it.currentDay == SimulationDay(2) },
                )
            }
        }
}
