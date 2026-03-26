package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.domain.model.StepRef
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulationEngineServiceClassBehaviorTest {
    @Test
    fun `given todo fixed_date and expedite cards with limited wip when running day then expedite starts first`() {
        val simulation =
            simulationWithTwoCards(
                first = Pair("fixed-date-card", ServiceClass.FIXED_DATE),
                second = Pair("expedite-card", ServiceClass.EXPEDITE),
                wipLimit = 1,
            )

        val result = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 3L)

        val allCards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        assertEquals(CardState.IN_PROGRESS, allCards.single { it.id == "expedite-card" }.state)
        assertEquals(CardState.TODO, allCards.single { it.id == "fixed-date-card" }.state)
    }

    @Test
    fun `given todo intangible and expedite cards with limited wip when running day then expedite starts first`() {
        val simulation =
            simulationWithTwoCards(
                first = Pair("intangible-card", ServiceClass.INTANGIBLE),
                second = Pair("expedite-card", ServiceClass.EXPEDITE),
                wipLimit = 1,
            )

        val result = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 3L)

        val allCards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        assertEquals(CardState.IN_PROGRESS, allCards.single { it.id == "expedite-card" }.state)
        assertEquals(CardState.TODO, allCards.single { it.id == "intangible-card" }.state)
    }

    @Test
    fun `given add item decision with fixed_date service class when running day then card has correct service class`() {
        val simulation = simulationForAddItem()

        val result =
            SimulationEngine.runDay(
                simulation = simulation,
                decisions = listOf(Decision.addItem(title = "Deadline", serviceClass = "FIXED_DATE")),
                seed = 2L,
            )

        val firstStep =
            result.simulation.scenario.board.steps
                .minByOrNull { it.position }!!
        assertEquals(ServiceClass.FIXED_DATE, firstStep.cards.single { it.title == "Deadline" }.serviceClass)
    }

    @Test
    fun `given add item decision with intangible service class when running day then card has correct service class`() {
        val simulation = simulationForAddItem()

        val result =
            SimulationEngine.runDay(
                simulation = simulation,
                decisions = listOf(Decision.addItem(title = "Research", serviceClass = "INTANGIBLE")),
                seed = 2L,
            )

        val firstStep =
            result.simulation.scenario.board.steps
                .minByOrNull { it.position }!!
        assertEquals(ServiceClass.INTANGIBLE, firstStep.cards.single { it.title == "Research" }.serviceClass)
    }

    private fun simulationWithTwoCards(
        first: Pair<String, ServiceClass>,
        second: Pair<String, ServiceClass>,
        wipLimit: Int,
    ): Simulation {
        val board =
            Board
                .create(name = "Flow")
                .addStep(name = "Input", requiredAbility = AbilityName.PRODUCT_MANAGER)
        val input = board.steps.first()
        val cards =
            listOf(
                Card(id = first.first, step = StepRef(input.id), title = first.first, serviceClass = first.second),
                Card(id = second.first, step = StepRef(input.id), title = second.first, serviceClass = second.second),
            )
        val rules = ScenarioRules.create(wipLimit = wipLimit, teamSize = 2, seedValue = 77L)
        val scenario =
            Scenario.create(
                name = "Scenario",
                rules = rules,
                board = board.copy(steps = listOf(input.copy(cards = cards))),
            )
        return Simulation.create(
            name = "Sim",
            organization = Organization.create(name = "Org"),
            scenario = scenario,
            status = SimulationStatus.RUNNING,
        )
    }

    private fun simulationForAddItem(): Simulation {
        val board =
            Board
                .create(name = "Flow")
                .addStep(name = "Input", requiredAbility = AbilityName.PRODUCT_MANAGER)
        val rules = ScenarioRules.create(wipLimit = 3, teamSize = 2, seedValue = 77L)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = board)
        return Simulation.create(
            name = "Sim",
            organization = Organization.create(name = "Org"),
            scenario = scenario,
            status = SimulationStatus.RUNNING,
        )
    }
}
