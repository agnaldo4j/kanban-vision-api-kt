package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationStatus
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
        assertEquals(CardState.IN_PROGRESS, allCards.single { it.id.value == "expedite-card" }.state)
        assertEquals(CardState.TODO, allCards.single { it.id.value == "fixed-date-card" }.state)
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
        assertEquals(CardState.IN_PROGRESS, allCards.single { it.id.value == "expedite-card" }.state)
        assertEquals(CardState.TODO, allCards.single { it.id.value == "intangible-card" }.state)
    }

    @Test
    fun `given add item decision with fixed_date service class when running day then card has correct service class`() {
        val simulation = simulationForAddItem()

        val result =
            SimulationEngine.runDay(
                simulation = simulation,
                decisions = listOf(Decision.AddItem(title = "Deadline", serviceClass = ServiceClass.FIXED_DATE)),
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
                decisions = listOf(Decision.AddItem(title = "Research", serviceClass = ServiceClass.INTANGIBLE)),
                seed = 2L,
            )

        val firstStep =
            result.simulation.scenario.board.steps
                .minByOrNull { it.position }!!
        assertEquals(ServiceClass.INTANGIBLE, firstStep.cards.single { it.title == "Research" }.serviceClass)
    }

    @Test
    fun `given todo fixed_date and standard cards with limited wip when running day then fixed_date starts first`() {
        val simulation =
            simulationWithTwoCards(
                first = Pair("standard-card", ServiceClass.STANDARD),
                second = Pair("fixed-date-card", ServiceClass.FIXED_DATE),
                wipLimit = 1,
            )

        val result = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 3L)

        val allCards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        assertEquals(CardState.IN_PROGRESS, allCards.single { it.id.value == "fixed-date-card" }.state)
        assertEquals(CardState.TODO, allCards.single { it.id.value == "standard-card" }.state)
    }

    @Test
    fun `given todo fixed_date and intangible cards with limited wip when running day then fixed_date starts first`() {
        val simulation =
            simulationWithTwoCards(
                first = Pair("intangible-card", ServiceClass.INTANGIBLE),
                second = Pair("fixed-date-card", ServiceClass.FIXED_DATE),
                wipLimit = 1,
            )

        val result = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 3L)

        val allCards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        assertEquals(CardState.IN_PROGRESS, allCards.single { it.id.value == "fixed-date-card" }.state)
        assertEquals(CardState.TODO, allCards.single { it.id.value == "intangible-card" }.state)
    }

    @Test
    fun `given todo standard and intangible cards with limited wip when running day then standard starts first`() {
        val simulation =
            simulationWithTwoCards(
                first = Pair("intangible-card", ServiceClass.INTANGIBLE),
                second = Pair("standard-card", ServiceClass.STANDARD),
                wipLimit = 1,
            )

        val result = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 3L)

        val allCards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        assertEquals(CardState.IN_PROGRESS, allCards.single { it.id.value == "standard-card" }.state)
        assertEquals(CardState.TODO, allCards.single { it.id.value == "intangible-card" }.state)
    }

    private fun simulationWithTwoCards(
        first: Pair<String, ServiceClass>,
        second: Pair<String, ServiceClass>,
        wipLimit: Int,
    ): Simulation {
        val board =
            Board
                .create(name = "Flow")
                .withStep(name = "Input", requiredAbility = AbilityName.PRODUCT_MANAGER)
        val input = board.steps.first()
        val cards =
            listOf(
                Card(id = CardId(first.first), step = input.id, title = first.first, serviceClass = first.second),
                Card(id = CardId(second.first), step = input.id, title = second.first, serviceClass = second.second),
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
                .withStep(name = "Input", requiredAbility = AbilityName.PRODUCT_MANAGER)
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
