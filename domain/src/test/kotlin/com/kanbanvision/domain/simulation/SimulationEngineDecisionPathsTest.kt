package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.DecisionType
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationEngineDecisionPathsTest {
    @Test
    fun `given malformed decision payloads when running day then engine ignores invalid commands`() {
        val simulation = baseSimulation()
        val decisions =
            listOf(
                Decision(type = DecisionType.MOVE_ITEM, payload = emptyMap()),
                Decision(type = DecisionType.BLOCK_ITEM, payload = emptyMap()),
                Decision(type = DecisionType.UNBLOCK_ITEM, payload = emptyMap()),
                Decision(type = DecisionType.ADD_ITEM, payload = emptyMap()),
            )

        val result = SimulationEngine.runDay(simulation, decisions, seed = 1L)
        val firstStepCards =
            result.simulation.scenario.board.steps
                .first()
                .cards

        assertEquals(1, firstStepCards.size)
    }

    @Test
    fun `given add item decisions with unknown and explicit class when running day then defaults and explicit classes are applied`() {
        val simulation = baseSimulation()
        val invalidServiceClass = Decision.addItem(title = "A", serviceClass = "UNKNOWN")
        val expedite = Decision.addItem(title = "B", serviceClass = ServiceClass.EXPEDITE.name)

        val result = SimulationEngine.runDay(simulation, listOf(invalidServiceClass, expedite), seed = 2L)
        val firstStep =
            result.simulation.scenario.board.steps
                .first()
        val cards = firstStep.cards

        assertEquals(3, cards.size)
        assertEquals(ServiceClass.STANDARD, cards[1].serviceClass)
        assertEquals(ServiceClass.EXPEDITE, cards[2].serviceClass)
    }

    @Test
    fun `given multiple assigned workers when running day then in progress card receives execution effort`() {
        val step = Step.create("board-1", "Dev", 0, AbilityName.DEVELOPER)
        val workerA = devWorker("w-a")
        val workerB = devWorker("w-b")
        val assignedStep = step.assignWorker(workerB).assignWorker(workerA)
        val card = Card(stepId = assignedStep.id, title = "Build", state = CardState.IN_PROGRESS, developmentEffort = 4)

        val board = Board(id = "board-1", name = "Main", steps = listOf(assignedStep.copy(cards = listOf(card))))
        val scenario = Scenario.create("Scenario", ScenarioRules.create(2, 2, 22L), board = board)
        val simulation = Simulation.create("Sim", Organization.create("Org"), scenario)

        val result = SimulationEngine.runDay(simulation, emptyList(), seed = 22L)
        val updatedStep =
            result.simulation.scenario.board.steps
                .first()
        val updated = updatedStep.cards.first()

        assertTrue(updated.remainingDevelopmentEffort < 4)
    }

    private fun baseSimulation(): Simulation {
        val step = Step.create("board-1", "Analysis", 0, AbilityName.PRODUCT_MANAGER)
        val card = Card(stepId = step.id, title = "Initial")
        val board = Board(id = "board-1", name = "Main", steps = listOf(step.copy(cards = listOf(card))))
        val scenario = Scenario.create("Scenario", ScenarioRules.create(2, 1, 1L), board = board)
        return Simulation.create("Sim", Organization.create("Org"), scenario)
    }

    private fun devWorker(id: String): Worker =
        Worker(
            id = id,
            name = "Dev",
            abilities =
                setOf(
                    Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL),
                    Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                ),
        )
}
