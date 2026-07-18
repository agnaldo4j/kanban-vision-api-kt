package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.kanban.Worker
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

class SimulationEngineDailyExecutionBehaviorTest {
    @Test
    fun `given simulation day when running engine then current day is advanced and snapshot is appended`() {
        val simulation = simulationWithCards(wipLimit = 2)

        val result = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 10L)

        assertEquals(simulation.currentDay.value + 1, result.simulation.currentDay.value)
        assertEquals(1, result.simulation.history.size)
        assertEquals(simulation.id, result.snapshot.simulation)
    }

    @Test
    fun `given in progress development card and assigned worker when running day then execution never increases remaining effort`() {
        val simulation = simulationWithCards(wipLimit = 2, devCardState = CardState.IN_PROGRESS, devRemainingEffort = 3)

        val result = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 1L)

        val developmentStep =
            result.simulation.scenario.board.steps
                .first { it.requiredAbility == AbilityName.DEVELOPER }
        val card = developmentStep.cards.single()
        assertTrue(card.remainingDevelopmentEffort <= 3)
    }

    @Test
    fun `given todo expedite and standard cards with limited wip when running day then expedite starts first`() {
        val simulation = simulationWithExpediteAndStandard(wipLimit = 1)

        val result = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 3L)

        val allCards =
            result.simulation.scenario.board.steps
                .flatMap { it.cards }
        val expediteCard = allCards.single { it.id.value == "expedite-card" }
        val standardCard = allCards.single { it.id.value == "standard-card" }

        assertEquals(CardState.IN_PROGRESS, expediteCard.state)
        assertEquals(CardState.TODO, standardCard.state)
        assertNotNull(
            result.snapshot.movements.firstOrNull {
                it.type == MovementType.MOVED && it.cardId.value == "expedite-card"
            },
        )
    }

    @Test
    fun `given add item decision when running day then new card is created at first board step`() {
        val simulation = simulationWithCards(wipLimit = 3)

        val result =
            SimulationEngine.runDay(
                simulation = simulation,
                decisions = listOf(Decision.AddItem(title = "Extra")),
                seed = 2L,
            )

        val firstStep =
            result.simulation.scenario.board.steps
                .minByOrNull { it.position }!!
        assertTrue(firstStep.cards.any { it.title == "Extra" })
    }

    private fun simulationWithCards(
        wipLimit: Int,
        devCardState: CardState = CardState.TODO,
        devRemainingEffort: Int = 2,
    ): Simulation {
        val boardWithSteps =
            Board
                .create(name = "Main")
                .addStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
                .addStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)

        val analysis = boardWithSteps.steps.first { it.requiredAbility == AbilityName.PRODUCT_MANAGER }
        val development = boardWithSteps.steps.first { it.requiredAbility == AbilityName.DEVELOPER }
        val boardWithWorker = boardWithAssignedDeveloper(boardWithSteps, development.id.value)
        val withCards =
            boardWithCards(
                board = boardWithWorker,
                stepIds = StepIds(analysis = analysis.id.value, development = development.id.value),
                developmentCard = DevelopmentCardSetup(state = devCardState, effort = devRemainingEffort),
            )

        val rules = ScenarioRules.create(wipLimit = wipLimit, teamSize = 4, seedValue = 99L)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = withCards)
        return Simulation.create(
            name = "Simulation",
            organization = Organization.create(name = "Org"),
            scenario = scenario,
            status = SimulationStatus.RUNNING,
        )
    }

    private fun boardWithAssignedDeveloper(
        board: Board,
        developmentStepId: String,
    ): Board {
        val developer =
            Worker(
                name = "Dev",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )

        return board.copy(
            steps =
                board.steps.map { step ->
                    if (step.id.value == developmentStepId) step.assignWorker(developer) else step
                },
        )
    }

    private fun boardWithCards(
        board: Board,
        stepIds: StepIds,
        developmentCard: DevelopmentCardSetup,
    ): Board =
        board.copy(
            steps =
                board.steps.map { step ->
                    when (step.id.value) {
                        stepIds.analysis -> step.copy(cards = listOf(Card(id = CardId("analysis-card"), step = step.id, title = "Spec")))
                        stepIds.development ->
                            step.copy(
                                cards =
                                    listOf(
                                        Card(
                                            id = CardId("dev-card"),
                                            step = step.id,
                                            title = "Build",
                                            state = developmentCard.state,
                                            developmentEffort = developmentCard.effort,
                                            remainingDevelopmentEffort = developmentCard.effort,
                                        ),
                                    ),
                            )

                        else -> step
                    }
                },
        )

    private data class StepIds(
        val analysis: String,
        val development: String,
    )

    private data class DevelopmentCardSetup(
        val state: CardState,
        val effort: Int,
    )

    private fun simulationWithExpediteAndStandard(wipLimit: Int): Simulation {
        val board =
            Board
                .create(name = "Flow")
                .addStep(name = "Input", requiredAbility = AbilityName.PRODUCT_MANAGER)

        val input = board.steps.first()
        val cards =
            listOf(
                Card(id = CardId("standard-card"), step = input.id, title = "Std", serviceClass = ServiceClass.STANDARD),
                Card(id = CardId("expedite-card"), step = input.id, title = "Exp", serviceClass = ServiceClass.EXPEDITE),
            )

        val boardWithCards = board.copy(steps = listOf(input.copy(cards = cards)))
        val rules = ScenarioRules.create(wipLimit = wipLimit, teamSize = 2, seedValue = 77L)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = boardWithCards)
        return Simulation.create(
            name = "Sim",
            organization = Organization.create(name = "Org"),
            scenario = scenario,
            status = SimulationStatus.RUNNING,
        )
    }
}
