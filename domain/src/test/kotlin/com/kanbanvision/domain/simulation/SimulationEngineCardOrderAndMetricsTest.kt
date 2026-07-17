package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.CardId
import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulationEngineCardOrderAndMetricsTest {
    @Test
    fun `given two expedite and two standard todo with wip limit 2 when running then both expedite start`() {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val cards =
            listOf(
                Card(id = CardId("s1"), step = step.id, title = "S1", serviceClass = ServiceClass.STANDARD),
                Card(id = CardId("s2"), step = step.id, title = "S2", serviceClass = ServiceClass.STANDARD),
                Card(id = CardId("e1"), step = step.id, title = "E1", serviceClass = ServiceClass.EXPEDITE),
                Card(id = CardId("e2"), step = step.id, title = "E2", serviceClass = ServiceClass.EXPEDITE),
            )
        val sim = boardSim(board.copy(steps = listOf(step.copy(cards = cards))), wipLimit = 2)

        val result = SimulationEngine.runDay(sim, emptyList(), seed = 42L)

        val allCards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        assertEquals(CardState.IN_PROGRESS, allCards.first { it.id.value == "e1" }.state)
        assertEquals(CardState.IN_PROGRESS, allCards.first { it.id.value == "e2" }.state)
        assertEquals(CardState.TODO, allCards.first { it.id.value == "s1" }.state)
        assertEquals(CardState.TODO, allCards.first { it.id.value == "s2" }.state)
    }

    @Test
    fun `given deployer worker and in progress card when running day then remaining deploy effort becomes zero`() {
        val sim = simWithWorkerAndCard(deployEffort = 5, remainingDeployEffort = 5)

        val result = SimulationEngine.runDay(sim, emptyList(), seed = 1L)

        val after =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first()
        assertEquals(0, after.remainingDeployEffort)
    }

    @Test
    fun `given mixed in progress blocked and done cards when metrics computed then all counts are exact`() {
        val board = Board.create("Board").addStep("Step", AbilityName.DEVELOPER)
        val step = board.steps.first()
        val cards =
            listOf(
                Card(id = CardId("wip1"), step = step.id, title = "W1", state = CardState.IN_PROGRESS),
                Card(id = CardId("wip2"), step = step.id, title = "W2", state = CardState.IN_PROGRESS),
                Card(id = CardId("blk1"), step = step.id, title = "B1", state = CardState.BLOCKED),
                Card(id = CardId("done1"), step = step.id, title = "D1", state = CardState.DONE),
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
                Card(id = CardId("c-pos2"), step = step.id, title = "P2", state = CardState.DONE, position = 2),
                Card(id = CardId("c-pos0"), step = step.id, title = "P0", state = CardState.DONE, position = 0),
                Card(id = CardId("c-pos1"), step = step.id, title = "P1", state = CardState.DONE, position = 1),
            )
        val sim = boardSim(board.copy(steps = listOf(step.copy(cards = cards))), wipLimit = 1)

        val result = SimulationEngine.runDay(sim, emptyList(), seed = 1L)

        val resultCards =
            result.simulation.scenario.board.steps
                .first()
                .cards
        assertEquals("c-pos0", resultCards[0].id.value)
        assertEquals("c-pos1", resultCards[1].id.value)
        assertEquals("c-pos2", resultCards[2].id.value)
    }

    // Uses DEPLOYER ability whose daily capacity is always Int.MAX_VALUE (not random 0..10),
    // making this helper deterministic regardless of the step ID's hash-based seed.
    private fun simWithWorkerAndCard(
        deployEffort: Int,
        remainingDeployEffort: Int,
    ): Simulation {
        val board = Board.create("Board").addStep("Deploy", AbilityName.DEPLOYER)
        val step = board.steps.first()
        val worker =
            Worker(
                id = "w1",
                name = "Deployer",
                abilities = setOf(Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL)),
            )
        val card =
            Card(
                id = CardId("c1"),
                step = step.id,
                title = "Task",
                state = CardState.IN_PROGRESS,
                deployEffort = deployEffort,
                remainingDeployEffort = remainingDeployEffort,
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
