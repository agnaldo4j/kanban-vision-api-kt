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
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulationEngineEdgeCaseBehaviorTest {
    @Test
    fun `given board with no steps when running day with add decision then no card is added`() {
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 1, seedValue = 1L)
        val scenario = Scenario.create(name = "S", rules = rules, board = Board.create("Empty"))
        val simulation =
            Simulation.create(
                name = "Sim",
                organization = Organization.create("Org"),
                scenario = scenario,
                status = SimulationStatus.RUNNING,
            )

        val result = SimulationEngine.runDay(simulation, decisions = listOf(Decision.AddItem("Card")), seed = 1L)

        assertEquals(
            0,
            result.simulation.scenario.board.steps
                .sumOf { it.cards.size },
        )
    }

    @Test
    fun `given step with worker and done card when running day then worker execution is skipped`() {
        val board = Board.create("B").addStep(name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val step = board.steps.first()
        val worker = Worker(name = "Dev", abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)))
        val card = Card(id = "c1", step = StepRef(step.id), title = "T", state = CardState.DONE)
        val boardWithCard = board.copy(steps = listOf(step.assignWorker(worker).copy(cards = listOf(card))))
        val rules = ScenarioRules.create(wipLimit = 3, teamSize = 1, seedValue = 1L)
        val scenario = Scenario.create(name = "S", rules = rules, board = boardWithCard)
        val simulation =
            Simulation.create(
                name = "Sim",
                organization = Organization.create("Org"),
                scenario = scenario,
                status = SimulationStatus.RUNNING,
            )

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 1L)

        assertEquals(
            CardState.DONE,
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first()
                .state,
        )
    }
}
