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

class SimulationEngineBehaviorTest {
    @Test
    fun `given simulation day run when decisions are applied then day advances and snapshot is appended`() {
        val (simulation, decisions) = simulationWithDecisionSet()

        val result = SimulationEngine.runDay(simulation, decisions, seed = 42L)

        assertEquals(simulation.currentDay.value + 1, result.simulation.currentDay.value)
        assertEquals(1, result.simulation.scenario.history.size)
        assertEquals(4, result.simulation.scenario.decisions.size)
        assertTrue(result.snapshot.movements.isNotEmpty())
    }

    @Test
    fun `given assigned worker on in progress card when running day then remaining effort is reduced`() {
        val simulation = simulationWithAssignedWorker()

        val result = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 10L)
        val updatedCard =
            result.simulation.scenario.board.steps
                .first()
                .cards
                .first()

        assertTrue(updatedCard.remainingDevelopmentEffort <= 5)
    }

    private fun simulationWithDecisionSet(): Pair<Simulation, List<Decision>> {
        val board =
            Board
                .create("Main")
                .addStep("Analysis", AbilityName.PRODUCT_MANAGER)
                .addStep("Dev", AbilityName.DEVELOPER)

        val analysis = board.steps.first()
        val baseCard =
            Card(
                stepId = analysis.id,
                title = "Item A",
                serviceClass = ServiceClass.EXPEDITE,
                analysisEffort = 1,
            )
        val boardWithCards = board.copy(steps = listOf(analysis.copy(cards = listOf(baseCard)), board.steps[1]))

        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 2, seedValue = 42L)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = boardWithCards)
        val simulation = Simulation.create(name = "Sim", organization = Organization.create("Org"), scenario = scenario)

        val decisions =
            listOf(
                Decision(type = DecisionType.MOVE_ITEM, payload = mapOf("cardId" to baseCard.id)),
                Decision(type = DecisionType.BLOCK_ITEM, payload = mapOf("cardId" to baseCard.id, "reason" to "dependency")),
                Decision(type = DecisionType.UNBLOCK_ITEM, payload = mapOf("cardId" to baseCard.id)),
                Decision(type = DecisionType.ADD_ITEM, payload = mapOf("title" to "New")),
            )
        return simulation to decisions
    }

    private fun simulationWithAssignedWorker(): Simulation {
        val step = assignedDevStep()

        val inProgressCard =
            Card(
                stepId = step.id,
                title = "Build",
                state = CardState.IN_PROGRESS,
                developmentEffort = 5,
                remainingDevelopmentEffort = 5,
            )

        val board = Board(id = "board-1", name = "Main", steps = listOf(step.copy(cards = listOf(inProgressCard))))
        val rules = ScenarioRules.create(wipLimit = 3, teamSize = 1, seedValue = 10L)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = board)
        return Simulation.create(name = "Sim", organization = Organization.create("Org"), scenario = scenario)
    }

    private fun assignedDevStep(): Step {
        val worker =
            Worker(
                name = "Dev",
                abilities =
                    setOf(
                        Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                    ),
            )

        return Step
            .create(
                boardId = "board-1",
                name = "Dev",
                position = 0,
                requiredAbility = AbilityName.DEVELOPER,
            ).assignWorker(worker)
    }
}
