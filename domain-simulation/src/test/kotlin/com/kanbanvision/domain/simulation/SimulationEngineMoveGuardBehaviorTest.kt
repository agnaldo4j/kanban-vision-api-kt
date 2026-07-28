package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards da decisão MoveItem (GAP-DU). Vive fora de SimulationEngineGuardBehaviorTest porque aquele
 * arquivo já está perto do limite de LargeClass do Detekt (200 linhas).
 */
class SimulationEngineMoveGuardBehaviorTest {
    @Test
    fun `given move decision on blocked card when running day then card stays blocked and no movement is recorded`() {
        val simulation = simulationWithSingleCard(cardId = "blocked", state = CardState.BLOCKED)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.MoveItem(CardId("blocked"))), seed = 1L)

        val card =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first { it.id.value == "blocked" }
        assertEquals(CardState.BLOCKED, card.state)
        assertTrue(result.snapshot.movements.none { it.cardId.value == "blocked" })
    }

    @Test
    fun `given move decision on blocked card when running day then blocked count stays one and wip count stays zero`() {
        val simulation = simulationWithSingleCard(cardId = "blocked", state = CardState.BLOCKED)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.MoveItem(CardId("blocked"))), seed = 1L)

        assertEquals(1, result.snapshot.metrics.blockedCount)
        assertEquals(0, result.snapshot.metrics.wipCount)
    }

    @Test
    fun `given move decision on blocked card and a todo card when running day then the freed wip slot starts the todo card`() {
        val simulation = simulationWithBlockedAndTodo(wipLimit = 1)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.MoveItem(CardId("blocked"))), seed = 1L)

        val cards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        assertEquals(CardState.BLOCKED, cards.first { it.id.value == "blocked" }.state)
        assertTrue(
            result.snapshot.movements.any {
                it.cardId.value == "todo" && it.type == MovementType.MOVED && it.reason == "auto: started"
            },
        )
    }

    private fun simulationWithSingleCard(
        cardId: String,
        state: CardState,
    ): Simulation {
        val board = Board.create("Board").withStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val card = Card(id = CardId(cardId), step = step.id, title = NonBlankTitle("Task"), state = state)
        return simulationFrom(board.copy(steps = listOf(step.copy(cards = listOf(card)))), wipLimit = 3)
    }

    private fun simulationWithBlockedAndTodo(wipLimit: Int): Simulation {
        val board = Board.create("Board").withStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val cards =
            listOf(
                Card(id = CardId("blocked"), step = step.id, title = NonBlankTitle("Blocked"), state = CardState.BLOCKED),
                Card(id = CardId("todo"), step = step.id, title = NonBlankTitle("Todo"), state = CardState.TODO),
            )
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
