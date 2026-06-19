package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.domain.model.StepRef
import com.kanbanvision.domain.model.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationEngineGuardBehaviorTest {
    @Test
    fun `given done card when running day then aging days do not increase`() {
        val simulation = simulationWithSingleCard(cardId = "done", state = CardState.DONE, agingDays = 5)

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        val card =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first { it.id == "done" }
        assertEquals(5, card.agingDays)
    }

    @Test
    fun `given in progress card when running day then aging days increase by one`() {
        val simulation = simulationWithSingleCard(cardId = "wip", state = CardState.IN_PROGRESS, agingDays = 3)

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        val card =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first { it.id == "wip" }
        assertEquals(4, card.agingDays)
    }

    @Test
    fun `given move decision on done card when running day then card remains done and no movement registered`() {
        val simulation = simulationWithSingleCard(cardId = "done", state = CardState.DONE)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.MoveItem("done")), seed = 1L)

        val card =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first { it.id == "done" }
        assertEquals(CardState.DONE, card.state)
        assertTrue(result.snapshot.movements.none { it.cardId == "done" })
    }

    @Test
    fun `given block decision on todo card when running day then no blocked movement is recorded`() {
        val simulation = simulationWithSingleCard(cardId = "todo", state = CardState.TODO)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.BlockItem("todo", "reason")), seed = 1L)

        assertTrue(result.snapshot.movements.none { it.cardId == "todo" && it.type == MovementType.BLOCKED })
    }

    @Test
    fun `given block decision on done card when running day then no blocked movement is recorded`() {
        val simulation = simulationWithSingleCard(cardId = "done", state = CardState.DONE)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.BlockItem("done", "reason")), seed = 1L)

        assertTrue(result.snapshot.movements.none { it.cardId == "done" && it.type == MovementType.BLOCKED })
    }

    @Test
    fun `given unblock decision on in progress card when running day then no unblocked movement is recorded`() {
        val simulation = simulationWithSingleCard(cardId = "wip", state = CardState.IN_PROGRESS)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.UnblockItem("wip")), seed = 1L)

        assertTrue(result.snapshot.movements.none { it.cardId == "wip" && it.type == MovementType.UNBLOCKED })
    }

    @Test
    fun `given unblock decision on todo card when running day then no unblocked movement is recorded`() {
        val simulation = simulationWithSingleCard(cardId = "todo", state = CardState.TODO)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.UnblockItem("todo")), seed = 1L)

        assertTrue(result.snapshot.movements.none { it.cardId == "todo" && it.type == MovementType.UNBLOCKED })
    }

    @Test
    fun `given cards in progress exactly at wip limit when running day then no additional cards start`() {
        val simulation = simulationWithWipAtLimit(wipLimit = 2, inProgressCount = 2, todoCount = 1)

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        val started = result.snapshot.movements.count { it.type == MovementType.MOVED && it.reason == "auto: started" }
        assertEquals(0, started)
    }

    @Test
    fun `given wip below limit when running day then todo cards start up to limit`() {
        val simulation = simulationWithWipAtLimit(wipLimit = 3, inProgressCount = 1, todoCount = 2)

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        val started = result.snapshot.movements.count { it.type == MovementType.MOVED && it.reason == "auto: started" }
        assertEquals(2, started)
    }

    @Test
    fun `given worker assigned but in progress card has zero remaining effort when running day then effort stays zero`() {
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
                developmentEffort = 5,
                remainingDevelopmentEffort = 0,
            )
        val stepWithWorker = step.assignWorker(worker).copy(cards = listOf(card))
        val simulation = simulationFrom(board.copy(steps = listOf(stepWithWorker)), wipLimit = 3)

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        val resultCard =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first()
        assertEquals(0, resultCard.remainingDevelopmentEffort)
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

    private fun simulationWithWipAtLimit(
        wipLimit: Int,
        inProgressCount: Int,
        todoCount: Int,
    ): Simulation {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val cards =
            (1..inProgressCount).map { i ->
                Card(id = "wip-$i", step = StepRef(step.id), title = "WIP $i", state = CardState.IN_PROGRESS)
            } +
                (1..todoCount).map { i ->
                    Card(id = "todo-$i", step = StepRef(step.id), title = "Todo $i", state = CardState.TODO)
                }
        return simulationFrom(board.copy(steps = listOf(step.copy(cards = cards))), wipLimit = wipLimit)
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
