package com.kanbanvision.usecases.scenario

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioConfig
import com.kanbanvision.domain.model.SimulationContext
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationResult
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.ports.SimulationEnginePort
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.commands.RunDayCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunDayUseCaseTest {
    private val scenarioRepository = mockk<ScenarioRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val simulationEngine = mockk<SimulationEnginePort>()
    private val useCase = RunDayUseCase(scenarioRepository, snapshotRepository, simulationEngine)

    private val scenarioId = "scenario-1"
    private val config = ScenarioConfig(wipLimit = 2, teamSize = 3, seedValue = 42L)
    private val scenario = Scenario(id = scenarioId, organizationId = "t-1", config = config)
    private val state = SimulationState.initial(config)
    private val command = RunDayCommand(scenarioId = scenarioId, decisions = emptyList())

    private val nextState = state.copy(currentDay = SimulationDay(2))
    private val snapshot =
        DailySnapshot(
            scenarioId = scenarioId,
            day = SimulationDay(1),
            metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0),
            movements = emptyList(),
        )
    private val simulationResult = SimulationResult(newState = nextState, snapshot = snapshot)

    @Test
    fun `execute runs day and returns snapshot`() =
        runTest {
            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns state.right()
            coEvery { snapshotRepository.findByDay(scenarioId, state.currentDay) } returns null.right()
            every { simulationEngine.runDay(any(), any(), any(), any()) } returns simulationResult
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
            coEvery { scenarioRepository.findById(scenarioId) } returns DomainError.ScenarioNotFound(scenarioId).left()

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
            every { simulationEngine.runDay(any(), any(), any(), any()) } returns simulationResult
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

    @Test
    fun `execute returns ValidationError when state context does not match scenario`() =
        runTest {
            val inconsistentState =
                state.copy(
                    context =
                        SimulationContext(
                            organizationId = "other-org",
                            boardId = "other-board",
                        ),
                )

            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns inconsistentState.right()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { snapshotRepository.save(any()) }
        }

    @Test
    fun `execute returns ValidationError when context assignment references unknown worker`() =
        runTest {
            val step =
                Step(
                    boardId = scenario.boardId,
                    name = "Development",
                    requiredAbility = AbilityName.DEVELOPER,
                )
            val inconsistentState =
                state.copy(
                    context =
                        SimulationContext(
                            organizationId = scenario.organizationId,
                            boardId = scenario.boardId,
                            steps = listOf(step),
                            workerAssignments = mapOf("missing-worker" to step.id),
                        ),
                )

            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns inconsistentState.right()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { snapshotRepository.save(any()) }
        }
}
