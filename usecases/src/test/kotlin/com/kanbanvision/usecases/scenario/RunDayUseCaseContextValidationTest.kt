package com.kanbanvision.usecases.scenario

import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioConfig
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.SimulationContext
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.domain.model.Squad
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.Tribe
import com.kanbanvision.domain.model.Worker
import com.kanbanvision.usecases.ports.SimulationEnginePort
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

class RunDayUseCaseContextValidationTest {
    private val scenarioRepository = mockk<ScenarioRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val simulationEngine = mockk<SimulationEnginePort>()
    private val useCase = RunDayUseCase(scenarioRepository, snapshotRepository, simulationEngine)

    private val scenarioId = "scenario-1"
    private val config = ScenarioConfig(wipLimit = 2, teamSize = 3, seedValue = 42L)
    private val scenario = Scenario(id = scenarioId, organizationId = "t-1", config = config)
    private val state = SimulationState.initial(config)
    private val command = RunDayCommand(scenarioId = scenarioId, decisions = emptyList())

    @Test
    fun `execute returns ValidationError when assignment step belongs to another board`() =
        runTest {
            val worker = workerWith(AbilityName.DEVELOPER, "worker-dev")
            val step = Step(boardId = "another-board", name = "Development", requiredAbility = AbilityName.DEVELOPER)
            val inconsistentState = state.withContext(scenario, step, worker)

            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns inconsistentState.right()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { snapshotRepository.save(any()) }
        }

    @Test
    fun `execute returns ValidationError when worker lacks required ability for step`() =
        runTest {
            val worker = workerWith(AbilityName.PRODUCT_MANAGER, "worker-pm")
            val step = Step(boardId = scenario.boardId, name = "Development", requiredAbility = AbilityName.DEVELOPER)
            val inconsistentState = state.withContext(scenario, step, worker)

            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns inconsistentState.right()

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { snapshotRepository.save(any()) }
        }

    private fun SimulationState.withContext(
        scenario: Scenario,
        step: Step,
        worker: Worker,
    ): SimulationState =
        copy(
            context =
                SimulationContext(
                    organizationId = scenario.organizationId,
                    boardId = scenario.boardId,
                    steps = listOf(step),
                    tribes = tribesWith(worker),
                    workerAssignments = mapOf(worker.id to step.id),
                ),
        )

    private fun workerWith(
        abilityName: AbilityName,
        workerId: String,
    ) = Worker(
        id = workerId,
        name = "Worker $workerId",
        abilities =
            setOf(
                Ability(
                    id = "ability-$workerId",
                    name = abilityName,
                    seniority = Seniority.PL,
                ),
            ),
    )

    private fun tribesWith(worker: Worker): List<Tribe> =
        listOf(
            Tribe(
                id = "tribe-1",
                name = "Tribe A",
                squads =
                    listOf(
                        Squad(
                            id = "squad-1",
                            name = "Squad A",
                            workers = listOf(worker),
                        ),
                    ),
            ),
        )
}
