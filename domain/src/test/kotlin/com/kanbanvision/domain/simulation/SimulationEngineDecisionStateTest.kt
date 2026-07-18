package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.ServiceClass
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

class SimulationEngineDecisionStateTest {
    @Test
    fun `given in progress card when block decision then card state becomes blocked`() {
        val sim = singleCard("c", CardState.IN_PROGRESS)

        val result = SimulationEngine.runDay(sim, listOf(Decision.BlockItem(CardId("c"), "wait")), seed = 1L)

        val card =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first { it.id.value == "c" }
        assertEquals(CardState.BLOCKED, card.state)
        assertTrue(result.snapshot.movements.any { it.cardId.value == "c" && it.type == MovementType.BLOCKED })
    }

    @Test
    fun `given blocked card when unblock decision then card state becomes in progress`() {
        val sim = singleCard("c", CardState.BLOCKED)

        val result = SimulationEngine.runDay(sim, listOf(Decision.UnblockItem(CardId("c"))), seed = 1L)

        val card =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first { it.id.value == "c" }
        assertEquals(CardState.IN_PROGRESS, card.state)
        assertTrue(result.snapshot.movements.any { it.cardId.value == "c" && it.type == MovementType.UNBLOCKED })
    }

    @Test
    fun `given add item decision when running day then card appears in first step with standard class`() {
        val sim = emptyBoardSim()

        val result = SimulationEngine.runDay(sim, listOf(Decision.AddItem("NewTask")), seed = 1L)

        val allCards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        assertEquals(1, allCards.size)
        assertEquals("NewTask", allCards.first().title)
        assertEquals(ServiceClass.STANDARD, allCards.first().serviceClass)
    }

    @Test
    fun `given add item with expedite service class when running day then card has expedite service class`() {
        val sim = emptyBoardSim()

        val result = SimulationEngine.runDay(sim, listOf(Decision.AddItem("Urgent", ServiceClass.EXPEDITE)), seed = 1L)

        val card =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first()
        assertEquals(ServiceClass.EXPEDITE, card.serviceClass)
    }

    @Test
    fun `given add item when two cards already in step then new card gets position equal to count`() {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val existing =
            listOf(
                Card(id = CardId("c0"), step = step.id, title = "C0", state = CardState.IN_PROGRESS, position = 0),
                Card(id = CardId("c1"), step = step.id, title = "C1", state = CardState.IN_PROGRESS, position = 1),
            )
        val sim = boardSim(board.copy(steps = listOf(step.copy(cards = existing))), wipLimit = 5)

        val result = SimulationEngine.runDay(sim, listOf(Decision.AddItem("New")), seed = 1L)

        val newCard =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first { it.title == "New" }
        assertEquals(2, newCard.position)
    }

    private fun singleCard(
        cardId: String,
        state: CardState,
    ): Simulation {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val card = Card(id = CardId(cardId), step = step.id, title = "Task", state = state)
        return boardSim(board.copy(steps = listOf(step.copy(cards = listOf(card)))), wipLimit = 3)
    }

    private fun emptyBoardSim(): Simulation {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        return boardSim(board, wipLimit = 3)
    }

    private fun boardSim(
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
