package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.domain.model.StepRef
import com.kanbanvision.domain.model.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationEngineCardOrderAndMetricsTest {
    @Test
    fun `given two expedite and two standard todo with wip limit 2 when running then both expedite start`() {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val cards =
            listOf(
                Card(id = "s1", step = StepRef(step.id), title = "S1", serviceClass = ServiceClass.STANDARD),
                Card(id = "s2", step = StepRef(step.id), title = "S2", serviceClass = ServiceClass.STANDARD),
                Card(id = "e1", step = StepRef(step.id), title = "E1", serviceClass = ServiceClass.EXPEDITE),
                Card(id = "e2", step = StepRef(step.id), title = "E2", serviceClass = ServiceClass.EXPEDITE),
            )
        val sim = boardSim(board.copy(steps = listOf(step.copy(cards = cards))), wipLimit = 2)

        val result = SimulationEngine.runDay(sim, emptyList(), seed = 42L)

        val allCards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        assertEquals(CardState.IN_PROGRESS, allCards.first { it.id == "e1" }.state)
        assertEquals(CardState.IN_PROGRESS, allCards.first { it.id == "e2" }.state)
        assertEquals(CardState.TODO, allCards.first { it.id == "s1" }.state)
        assertEquals(CardState.TODO, allCards.first { it.id == "s2" }.state)
    }

    @Test
    fun `given worker with effort and in progress card when running day then remaining effort decreases`() {
        val sim = simWithWorkerAndCard(developmentEffort = 10, remainingEffort = 10)

        val result = SimulationEngine.runDay(sim, emptyList(), seed = 1L)

        val after =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first()
        assertTrue(after.remainingDevelopmentEffort < 10)
    }

    @Test
    fun `given mixed in progress blocked and done cards when metrics computed then all counts are exact`() {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val cards =
            listOf(
                Card(id = "wip1", step = StepRef(step.id), title = "W1", state = CardState.IN_PROGRESS),
                Card(id = "wip2", step = StepRef(step.id), title = "W2", state = CardState.IN_PROGRESS),
                Card(id = "blk1", step = StepRef(step.id), title = "B1", state = CardState.BLOCKED),
                Card(id = "done1", step = StepRef(step.id), title = "D1", state = CardState.DONE),
            )
        val sim = boardSim(board.copy(steps = listOf(step.copy(cards = cards))), wipLimit = 5)

        val result = SimulationEngine.runDay(sim, emptyList(), seed = 1L)

        assertEquals(2, result.snapshot.metrics.wipCount)
        assertEquals(1, result.snapshot.metrics.blockedCount)
        assertEquals(0, result.snapshot.metrics.throughput)
    }

    @Test
    fun `given cards with different positions when board rebuilt after run then lower position cards come first`() {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val cards =
            listOf(
                Card(id = "c-pos2", step = StepRef(step.id), title = "P2", state = CardState.DONE, position = 2),
                Card(id = "c-pos0", step = StepRef(step.id), title = "P0", state = CardState.DONE, position = 0),
                Card(id = "c-pos1", step = StepRef(step.id), title = "P1", state = CardState.DONE, position = 1),
            )
        val sim = boardSim(board.copy(steps = listOf(step.copy(cards = cards))), wipLimit = 1)

        val result = SimulationEngine.runDay(sim, emptyList(), seed = 1L)

        val resultCards =
            result.simulation.scenario.board.steps
                .first()
                .cards
        assertEquals("c-pos0", resultCards[0].id)
        assertEquals("c-pos1", resultCards[1].id)
        assertEquals("c-pos2", resultCards[2].id)
    }

    private fun simWithWorkerAndCard(
        developmentEffort: Int,
        remainingEffort: Int,
    ): Simulation {
        val board = Board.create("Board").addStep("Dev", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val worker =
            Worker(
                id = "w1",
                name = "Dev",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val card =
            Card(
                id = "c1",
                step = StepRef(step.id),
                title = "Task",
                state = CardState.IN_PROGRESS,
                developmentEffort = developmentEffort,
                remainingDevelopmentEffort = remainingEffort,
            )
        return boardSim(board.copy(steps = listOf(step.assignWorker(worker).copy(cards = listOf(card)))), wipLimit = 3)
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
