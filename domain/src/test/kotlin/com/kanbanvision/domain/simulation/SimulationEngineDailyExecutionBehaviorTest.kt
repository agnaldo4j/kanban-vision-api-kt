package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.MovementType
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationEngineDailyExecutionBehaviorTest {
    @Test
    fun `given simulation day when running engine then current day is advanced and snapshot is appended`() {
        val simulation = simulationWithCards(wipLimit = 2)

        val result = SimulationEngine.runDay(simulation = simulation, decisions = emptyList(), seed = 10L)

        assertEquals(simulation.currentDay.value + 1, result.simulation.currentDay.value)
        assertEquals(1, result.simulation.scenario.history.size)
        assertEquals(simulation.id, result.snapshot.simulation.id)
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
        val expediteCard = allCards.single { it.id == "expedite-card" }
        val standardCard = allCards.single { it.id == "standard-card" }

        assertEquals(CardState.IN_PROGRESS, expediteCard.state)
        assertEquals(CardState.TODO, standardCard.state)
        assertNotNull(
            result.snapshot.movements.firstOrNull {
                it.type == MovementType.MOVED && it.cardId == "expedite-card"
            },
        )
    }

    @Test
    fun `given add item decision when running day then new card is created at first board step`() {
        val simulation = simulationWithCards(wipLimit = 3)

        val result =
            SimulationEngine.runDay(
                simulation = simulation,
                decisions = listOf(Decision.addItem(title = "Extra", serviceClass = "STANDARD")),
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
        val boardWithWorker = boardWithAssignedDeveloper(boardWithSteps, development.id)
        val withCards =
            boardWithCards(
                board = boardWithWorker,
                stepIds = StepIds(analysis = analysis.id, development = development.id),
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
                    if (step.id == developmentStepId) step.assignWorker(developer) else step
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
                    when (step.id) {
                        stepIds.analysis -> step.copy(cards = listOf(Card(id = "analysis-card", step = StepRef(step.id), title = "Spec")))
                        stepIds.development ->
                            step.copy(
                                cards =
                                    listOf(
                                        Card(
                                            id = "dev-card",
                                            step = StepRef(step.id),
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
                Card(id = "standard-card", step = StepRef(input.id), title = "Std", serviceClass = ServiceClass.STANDARD),
                Card(id = "expedite-card", step = StepRef(input.id), title = "Exp", serviceClass = ServiceClass.EXPEDITE),
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
