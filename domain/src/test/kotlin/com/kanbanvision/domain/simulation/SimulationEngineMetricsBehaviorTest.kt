package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.StepRef
import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.Scenario
import com.kanbanvision.domain.model.organization.ScenarioRules
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationEngineMetricsBehaviorTest {
    @Test
    fun `given two in progress and one done card when metrics computed then wip count is two and blocked is zero`() {
        val simulation = simulationWithMixedCards()

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        assertEquals(2, result.snapshot.metrics.wipCount)
        assertEquals(0, result.snapshot.metrics.blockedCount)
    }

    @Test
    fun `given blocked card when running day then blocked count is one and wip count is zero`() {
        val simulation = simulationWithSingleCard(cardId = "blocked", state = CardState.BLOCKED)

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        assertEquals(1, result.snapshot.metrics.blockedCount)
        assertEquals(0, result.snapshot.metrics.wipCount)
    }

    @Test
    fun `given all done cards when metrics computed then avg aging days is zero`() {
        val simulation = simulationWithSingleCard(cardId = "done", state = CardState.DONE, agingDays = 10)

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        assertEquals(0.0, result.snapshot.metrics.avgAgingDays)
    }

    @Test
    fun `given in progress card with aging days 4 when running day then avg aging days is 5`() {
        val simulation = simulationWithSingleCard(cardId = "wip", state = CardState.IN_PROGRESS, agingDays = 4)

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        assertEquals(5.0, result.snapshot.metrics.avgAgingDays)
    }

    @Test
    fun `given move decision completing in progress card when metrics computed then throughput is one`() {
        val simulation = simulationWithSingleCard(cardId = "wip", state = CardState.IN_PROGRESS)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.MoveItem("wip")), seed = 1L)

        assertEquals(1, result.snapshot.metrics.throughput)
        assertTrue(result.snapshot.movements.any { it.type == MovementType.COMPLETED && it.cardId == "wip" })
    }

    @Test
    fun `given no completed movements when metrics computed then throughput is zero`() {
        val simulation = simulationWithSingleCard(cardId = "todo", state = CardState.TODO)

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        assertEquals(0, result.snapshot.metrics.throughput)
    }

    @Test
    fun `given same simulation state when running same day with same seed twice then effort reduction is identical`() {
        val simulation = simulationWithWorkerAndCard()

        val resultA = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 999L)
        val resultB = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 999L)

        val effortA =
            resultA.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first()
                .remainingDevelopmentEffort
        val effortB =
            resultB.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first()
                .remainingDevelopmentEffort
        assertEquals(effortA, effortB)
    }

    @Test
    fun `given any seed when running day then snapshot references the input simulation`() {
        val simulation = simulationWithWorkerAndCard()

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 0L)

        assertNotNull(result.snapshot)
        assertEquals(simulation.id, result.snapshot.simulation.id)
    }

    private fun simulationWithSingleCard(
        cardId: String,
        state: CardState,
        agingDays: Int = 0,
    ): Simulation {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val card = Card(id = cardId, step = StepRef(step.id), title = "Task", state = state, agingDays = agingDays)
        return simulationFrom(board.copy(steps = listOf(step.copy(cards = listOf(card)))), wipLimit = 3)
    }

    private fun simulationWithMixedCards(): Simulation {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val cards =
            listOf(
                Card(id = "wip-1", step = StepRef(step.id), title = "WIP 1", state = CardState.IN_PROGRESS),
                Card(id = "wip-2", step = StepRef(step.id), title = "WIP 2", state = CardState.IN_PROGRESS),
                Card(id = "done-1", step = StepRef(step.id), title = "Done 1", state = CardState.DONE),
            )
        return simulationFrom(board.copy(steps = listOf(step.copy(cards = cards))), wipLimit = 5)
    }

    private fun simulationWithWorkerAndCard(): Simulation {
        val board = Board.create("Board").addStep("Dev", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val worker =
            Worker(
                id = "worker-1",
                name = "Dev",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val card =
            Card(
                id = "card",
                step = StepRef(step.id),
                title = "Task",
                state = CardState.IN_PROGRESS,
                developmentEffort = 10,
                remainingDevelopmentEffort = 10,
            )
        val stepWithWorker = step.assignWorker(worker).copy(cards = listOf(card))
        return simulationFrom(board.copy(steps = listOf(stepWithWorker)), wipLimit = 3)
    }

    private fun simulationFrom(
        board: Board,
        wipLimit: Int,
    ): Simulation {
        val rules = ScenarioRules.create(wipLimit = wipLimit, teamSize = 2, seedValue = 1L)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = board)
        return Simulation.create(
            name = "Simulation",
            organization = Organization.create("Org"),
            scenario = scenario,
            status = SimulationStatus.RUNNING,
        )
    }
}
