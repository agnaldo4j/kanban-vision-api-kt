package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationEngineDecisionBehaviorTest {
    @Test
    fun `given move decision on in progress card when running day then movement is registered`() {
        val simulation = simulationWithCard(cardId = "card-1", state = CardState.IN_PROGRESS)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.move("card-1")), seed = 1L)

        assertNotNull(result.snapshot.movements.firstOrNull { it.cardId == "card-1" && it.type == MovementType.COMPLETED })
    }

    @Test
    fun `given block and unblock decisions when running day then blocked card returns to in progress`() {
        val simulation = simulationWithCard(cardId = "card-2", state = CardState.IN_PROGRESS)

        val blocked =
            SimulationEngine
                .runDay(
                    simulation,
                    decisions = listOf(Decision.block(cardId = "card-2", reason = "waiting")),
                    seed = 2L,
                ).simulation

        val unblocked =
            SimulationEngine.runDay(
                blocked,
                decisions = listOf(Decision.unblock(cardId = "card-2")),
                seed = 3L,
            )

        val card =
            unblocked.simulation.scenario.board.steps
                .first()
                .cards
                .first { it.id == "card-2" }
        assertEquals(CardState.IN_PROGRESS, card.state)
        assertTrue(unblocked.snapshot.movements.any { it.type == MovementType.UNBLOCKED && it.cardId == "card-2" })
    }

    @Test
    fun `given unknown card decision when running day then engine ignores decision safely`() {
        val simulation = simulationWithCard(cardId = "card-3", state = CardState.TODO)

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.move("missing-card")), seed = 4L)

        assertEquals(0, result.snapshot.movements.count { it.reason.startsWith("decision:") })
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
                            cards = listOf(Card(id = cardId, stepId = step.id, title = "Task", state = state)),
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
