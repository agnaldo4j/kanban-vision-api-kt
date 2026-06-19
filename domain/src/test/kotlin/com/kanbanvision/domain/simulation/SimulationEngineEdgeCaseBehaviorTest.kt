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
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.domain.model.StepRef
import com.kanbanvision.domain.model.Worker
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
        val decision = Decision(type = DecisionType.ADD_ITEM, payload = mapOf("title" to "Card"))

        val result = SimulationEngine.runDay(simulation, decisions = listOf(decision), seed = 1L)

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

    @Test
    fun `given add decision without title in payload when running day then no card is added`() {
        val board = Board.create("B").addStep(name = "Execution", requiredAbility = AbilityName.DEVELOPER)
        val step = board.steps.first()
        val card = Card(id = "c1", step = StepRef(step.id), title = "Task", state = CardState.DONE)
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 1, seedValue = 1L)
        val scenario = Scenario.create(name = "S", rules = rules, board = board.copy(steps = listOf(step.copy(cards = listOf(card)))))
        val simulation =
            Simulation.create(
                name = "Sim",
                organization = Organization.create("Org"),
                scenario = scenario,
                status = SimulationStatus.RUNNING,
            )
        val decision = Decision(type = DecisionType.ADD_ITEM, payload = emptyMap())

        val result = SimulationEngine.runDay(simulation, decisions = listOf(decision), seed = 1L)

        assertEquals(
            1,
            result.simulation.scenario.board.steps
                .sumOf { it.cards.size },
        )
    }

    @Test
    fun `given add decision with invalid service class string when running day then card defaults to standard`() {
        val board = Board.create("B").addStep(name = "Execution", requiredAbility = AbilityName.DEVELOPER)
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 1, seedValue = 1L)
        val scenario = Scenario.create(name = "S", rules = rules, board = board)
        val simulation =
            Simulation.create(
                name = "Sim",
                organization = Organization.create("Org"),
                scenario = scenario,
                status = SimulationStatus.RUNNING,
            )
        val decision = Decision(type = DecisionType.ADD_ITEM, payload = mapOf("title" to "New", "serviceClass" to "INVALID"))

        val result = SimulationEngine.runDay(simulation, decisions = listOf(decision), seed = 1L)

        val added =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
                .first { it.title == "New" }
        assertEquals(ServiceClass.STANDARD, added.serviceClass)
    }
}
