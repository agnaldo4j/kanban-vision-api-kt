package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AggregateContractTest {
    @Test
    fun `given organization board and scenario aggregates when reading components then aggregate boundaries are explicit`() {
        val squad = Squad(name = "Squad", workers = listOf(devWorker("w-1")))
        val tribe = Tribe(name = "Tribe", squads = listOf(squad))
        val organization = Organization.create(name = "Org", tribes = listOf(tribe))
        val board = Board.create("Main").addStep("Dev", AbilityName.DEVELOPER)
        val scenario = Scenario.create(name = "Scenario", rules = ScenarioRules.create(2, 1, 1L), board = board)

        assertEquals("Org", organization.component2())
        assertEquals(1, organization.component3().size)

        assertEquals("Main", board.component2())
        assertEquals(1, board.component3().size)

        assertEquals("Scenario", scenario.component2())
        assertEquals(2, scenario.component3().wipLimit)
        assertEquals(board.id, scenario.component4().id)
        assertTrue(scenario.component5().isEmpty())
        assertTrue(scenario.component6().isEmpty())
    }

    @Test
    fun `given step and card aggregates when reading components then workflow state is explicit`() {
        val worker = devWorker("w-2")
        val step = Step.create("b-1", "Dev", 0, AbilityName.DEVELOPER).assignWorker(worker)
        val card = Card(stepId = step.id, title = "Card", state = CardState.IN_PROGRESS, developmentEffort = 2)
        assertEquals(0, step.component4())
        assertEquals(AbilityName.DEVELOPER, step.component5())
        assertEquals(0, step.component6().size)
        assertEquals(1, step.component7().size)

        assertEquals(step.id, card.component2())
        assertEquals("Card", card.component3())
        assertEquals(0, card.component13())
        assertEquals(2, card.component14())
        assertEquals(0, card.component15())
        assertEquals(0, card.component16())
    }

    @Test
    fun `given simulation result aggregate when reading components then simulation envelope is explicit`() {
        val scenarioBoard = Board(id = "b-1", name = "Board", steps = emptyList())
        val scenario = Scenario.create("Scenario", ScenarioRules.create(2, 1, 1L), board = scenarioBoard)
        val simulation = Simulation.create("Sim", Organization.create("Org"), scenario)
        val snapshot =
            DailySnapshot(
                simulationId = simulation.id,
                day = simulation.currentDay,
                metrics = FlowMetrics(throughput = 0, wipCount = 1, blockedCount = 0, avgAgingDays = 0.0),
                movements = emptyList(),
            )
        val result = SimulationResult(simulation = simulation, snapshot = snapshot)

        assertEquals("Sim", simulation.component2())
        assertEquals(1, simulation.component3().value)
        assertEquals(SimulationStatus.DRAFT, simulation.component4())
        assertEquals("Org", simulation.component5().name)
        assertEquals("Scenario", simulation.component6().name)

        assertEquals(simulation.id, result.simulation.id)
        assertEquals(snapshot.id, result.snapshot.id)
    }

    private fun devWorker(id: String): Worker =
        Worker(
            id = id,
            name = "Dev",
            abilities =
                setOf(
                    Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL),
                    Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                ),
        )
}
