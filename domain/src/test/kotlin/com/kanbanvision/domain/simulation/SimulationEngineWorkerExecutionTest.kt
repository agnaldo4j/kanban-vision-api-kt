package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.ScenarioConfig
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.SimulationContext
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.domain.model.Squad
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.Tribe
import com.kanbanvision.domain.model.Worker
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationEngineWorkerExecutionTest {
    private val scenarioId = UUID.randomUUID().toString()
    private val config = ScenarioConfig(wipLimit = 2, teamSize = 3, seedValue = 42L)
    private val emptyState = SimulationState.initial(config)

    @Test
    fun `assigned worker execution consumes effort for card in matching step`() {
        val state = assignedExecutionState()

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)
        val updated = result.newState.cards.first()

        assertEquals(0, updated.remainingDeployEffort)
    }

    @Test
    fun `assigned execution is ignored when assignment references unknown worker`() {
        val step = deployStep()
        val context = deployContext(step, deployWorker()).copy(workerAssignments = mapOf("missing-worker" to step.id))
        val item = deployCard(step)
        val state = emptyState.copy(cards = listOf(item), context = context)

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)
        val updated = result.newState.cards.first()

        assertEquals(3, updated.remainingDeployEffort)
    }

    @Test
    fun `assigned execution is ignored when no executable card in assigned step`() {
        val step = deployStep()
        val worker = deployWorker()
        val context = deployContext(step, worker)
        val doneCard = deployCard(step).copy(state = CardState.DONE, remainingDeployEffort = 0)
        val state = emptyState.copy(cards = listOf(doneCard), context = context)

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)
        val updated = result.newState.cards.first()

        assertTrue(updated.state == CardState.DONE)
        assertEquals(0, updated.remainingDeployEffort)
    }

    @Test
    fun `assigned execution skips incompatible worker-step assignment instead of throwing`() {
        val step = deployStep()
        val incompatibleWorker =
            Worker(
                id = "worker-dev-only",
                name = "Dev Only",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val context = deployContext(step, incompatibleWorker)
        val item = deployCard(step)
        val state = emptyState.copy(cards = listOf(item), context = context)

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)
        val updated = result.newState.cards.first()

        assertEquals(3, updated.remainingDeployEffort)
    }

    @Test
    fun `assigned execution is deterministic regardless of workerAssignments map insertion order`() {
        val step = deployStep()
        val lowCapacityWorker = deployWorker(id = "worker-a", seniority = Seniority.JR)
        val highCapacityWorker = deployWorker(id = "worker-z", seniority = Seniority.SR)
        val cards = executionCardsForDeterminism(step)
        val tribes = tribesForWorkers(lowCapacityWorker, highCapacityWorker)
        val contextAB = contextWithAssignments(step, tribes, linkedMapOf(lowCapacityWorker.id to step.id, highCapacityWorker.id to step.id))
        val contextBA = contextAB.copy(workerAssignments = assignmentsBA)

        val resultAB = SimulationEngine.runDay(scenarioId, emptyState.copy(cards = cards, context = contextAB), emptyList(), seed = 99L)
        val resultBA = SimulationEngine.runDay(scenarioId, emptyState.copy(cards = cards, context = contextBA), emptyList(), seed = 99L)

        assertEquals(
            resultAB.newState.cards.map { it.remainingDeployEffort },
            resultBA.newState.cards.map { it.remainingDeployEffort },
        )
    }

    private val assignmentsBA = linkedMapOf("worker-z" to "step-deploy", "worker-a" to "step-deploy")

    private fun assignedExecutionState(): SimulationState {
        val step = deployStep()
        val worker = deployWorker()
        val context = deployContext(step, worker)
        val item = deployCard(step)
        return emptyState.copy(cards = listOf(item), context = context)
    }

    private fun deployStep() =
        Step(
            id = "step-deploy",
            boardId = "board-1",
            name = "Deploy",
            requiredAbility = AbilityName.DEPLOYER,
        )

    private fun deployWorker(
        id: String = "worker-1",
        seniority: Seniority = Seniority.PL,
    ): Worker =
        Worker(
            id = id,
            name = "Worker $id",
            abilities =
                setOf(
                    Ability(name = AbilityName.TESTER, seniority = seniority),
                    Ability(name = AbilityName.DEPLOYER, seniority = seniority),
                ),
        )

    private fun deployContext(
        step: Step,
        worker: Worker,
    ) = SimulationContext(
        organizationId = "org-1",
        boardId = "board-1",
        steps = listOf(step),
        tribes = listOf(Tribe(name = "Tribe A", squads = listOf(Squad(name = "Squad A", workers = listOf(worker))))),
        workerAssignments = mapOf(worker.id to step.id),
    )

    private fun contextWithAssignments(
        step: Step,
        tribes: List<Tribe>,
        assignments: Map<String, String>,
    ) = SimulationContext(
        organizationId = "org-1",
        boardId = "board-1",
        steps = listOf(step),
        tribes = tribes,
        workerAssignments = assignments,
    )

    private fun tribesForWorkers(vararg workers: Worker) =
        listOf(
            Tribe(
                id = "tribe-1",
                name = "Tribe A",
                squads = listOf(Squad(id = "squad-1", name = "Squad A", workers = workers.toList())),
            ),
        )

    private fun executionCardsForDeterminism(step: Step) =
        listOf(
            deployCard(step).copy(id = "card-1", deployEffort = 6, remainingDeployEffort = 6),
            deployCard(step).copy(id = "card-2", deployEffort = 6, remainingDeployEffort = 6),
        )

    private fun deployCard(step: Step) =
        Card(
            stepId = step.id,
            title = "Task Effort",
            state = CardState.IN_PROGRESS,
            analysisEffort = 0,
            developmentEffort = 0,
            testEffort = 0,
            deployEffort = 3,
        )
}
