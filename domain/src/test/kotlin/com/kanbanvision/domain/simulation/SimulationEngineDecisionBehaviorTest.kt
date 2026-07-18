package com.kanbanvision.domain.simulation

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationEngineDecisionBehaviorTest {
    @Test
    fun `given move decision on in progress card when running day then movement is registered`() {
        val simulation = simulationWithCard(cardId = "card-1", state = CardState.IN_PROGRESS)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.MoveItem(CardId("card-1"))), seed = 1L)

        assertNotNull(result.snapshot.movements.firstOrNull { it.cardId.value == "card-1" && it.type == MovementType.COMPLETED })
    }

    @Test
    fun `given block and unblock decisions when running day then blocked card returns to in progress`() {
        val simulation = simulationWithCard(cardId = "card-2", state = CardState.IN_PROGRESS)

        val blocked =
            SimulationEngine
                .runDay(
                    simulation,
                    decisions = listOf(Decision.BlockItem(cardId = CardId("card-2"), reason = "waiting")),
                    seed = 2L,
                ).simulation

        val unblocked =
            SimulationEngine.runDay(
                blocked,
                decisions = listOf(Decision.UnblockItem(cardId = CardId("card-2"))),
                seed = 3L,
            )

        val card =
            unblocked.simulation.scenario.board.steps
                .first()
                .cards
                .first { it.id.value == "card-2" }
        assertEquals(CardState.IN_PROGRESS, card.state)
        assertTrue(unblocked.snapshot.movements.any { it.type == MovementType.UNBLOCKED && it.cardId.value == "card-2" })
    }

    @Test
    fun `given unknown card decision when running day then engine ignores decision safely`() {
        val simulation = simulationWithCard(cardId = "card-3", state = CardState.TODO)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.MoveItem(CardId("missing-card"))), seed = 4L)

        assertTrue(result.snapshot.movements.none { it.cardId.value == "missing-card" })
        val card =
            result.simulation.scenario.board.steps
                .first()
                .cards
                .first { it.id.value == "card-3" }
        assertEquals(CardState.IN_PROGRESS, card.state)
    }

    @Test
    fun `given move decision on todo card when running day then movement type is moved`() {
        val simulation = simulationWithCard(cardId = "card-t", state = CardState.TODO)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.MoveItem(CardId("card-t"))), seed = 1L)

        assertTrue(result.snapshot.movements.any { it.cardId.value == "card-t" && it.type == MovementType.MOVED })
    }

    @Test
    fun `given block decision on unknown card when running day then no blocked movement`() {
        val simulation = simulationWithCard(cardId = "card-1", state = CardState.IN_PROGRESS)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.BlockItem(CardId("unknown"))), seed = 1L)

        assertTrue(result.snapshot.movements.none { it.type == MovementType.BLOCKED })
    }

    @Test
    fun `given block decision without reason when running day then default reason is used`() {
        val simulation = simulationWithCard(cardId = "card-1", state = CardState.IN_PROGRESS)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.BlockItem(CardId("card-1"))), seed = 1L)

        assertEquals(
            "blocked",
            result.snapshot.movements
                .first { it.type == MovementType.BLOCKED }
                .reason,
        )
    }

    @Test
    fun `given unblock decision on unknown card when running day then no unblocked movement`() {
        val simulation = simulationWithCard(cardId = "card-1", state = CardState.BLOCKED)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.UnblockItem(CardId("unknown"))), seed = 1L)

        assertTrue(result.snapshot.movements.none { it.type == MovementType.UNBLOCKED })
    }

    private fun simulationWithCard(
        cardId: String,
        state: CardState,
    ): Simulation {
        val board =
            Board
                .create("Board")
                .addStep(name = "Execution", requiredAbility = AbilityName.DEVELOPER)

        val step = board.steps.first()
        val boardWithCard =
            board.copy(
                steps =
                    listOf(
                        step.copy(
                            cards = listOf(Card(id = CardId(cardId), step = step.id, title = "Task", state = state)),
                        ),
                    ),
            )
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 2, seedValue = 11L)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = boardWithCard)
        return Simulation.create(
            name = "Simulation",
            organization = Organization.create("Org"),
            scenario = scenario,
            status = SimulationStatus.RUNNING,
        )
    }
}
