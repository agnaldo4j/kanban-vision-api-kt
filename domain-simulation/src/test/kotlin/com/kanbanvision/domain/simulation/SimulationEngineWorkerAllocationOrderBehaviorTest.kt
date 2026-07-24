package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationEngineWorkerAllocationOrderBehaviorTest {
    @Test
    fun `given two assigned workers on same step when running day then engine executes deterministically without invalid state`() {
        val simulation = simulationWithTwoWorkersOnDevelopmentStep()

        val resultRun1 = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 5L)
        val resultRun2 = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 5L)

        val updatedCardRun1 =
            resultRun1.simulation.scenario.board.steps
                .first()
                .cards
                .first()
        val updatedCardRun2 =
            resultRun2.simulation.scenario.board.steps
                .first()
                .cards
                .first()

        assertTrue(updatedCardRun1.remainingDevelopmentEffort in 0..6)
        assertEquals(updatedCardRun1.remainingDevelopmentEffort, updatedCardRun2.remainingDevelopmentEffort)
    }

    private fun simulationWithTwoWorkersOnDevelopmentStep(): Simulation {
        val board = Board.create("Board").withStep("Development", AbilityName.DEVELOPER)
        val stepWithWorkers =
            board.steps
                .first()
                .withWorker(workerB())
                .withWorker(workerA())
        val card =
            Card(
                id = CardId("card-1"),
                step = stepWithWorkers.id,
                title = "Task",
                state = CardState.IN_PROGRESS,
                developmentEffort = 6,
                remainingDevelopmentEffort = 6,
            )
        val boardWithWork = board.copy(steps = listOf(stepWithWorkers.copy(cards = listOf(card))))
        return simulationFromBoard(boardWithWork)
    }

    private fun workerA(): Worker =
        Worker(
            id = "worker-a",
            name = "A",
            abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
        )

    private fun workerB(): Worker =
        Worker(
            id = "worker-b",
            name = "B",
            abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
        )

    private fun simulationFromBoard(boardWithWork: Board): Simulation {
        val scenario =
            Scenario.create(
                name = "Scenario",
                rules = ScenarioRules.create(wipLimit = 2, teamSize = 2, seedValue = 123L),
                board = boardWithWork,
            )
        return Simulation.create(
            name = "Simulation",
            organization = Organization.create("Org"),
            scenario = scenario,
            status = SimulationStatus.RUNNING,
        )
    }
}
