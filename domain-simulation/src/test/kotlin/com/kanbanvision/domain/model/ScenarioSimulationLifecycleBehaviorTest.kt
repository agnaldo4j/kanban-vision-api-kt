package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScenarioSimulationLifecycleBehaviorTest {
    @Test
    fun `given scenario rules inputs when creating rules then policy set and constraints are preserved`() {
        val rules = ScenarioRules.create(wipLimit = 3, teamSize = 4, seedValue = 42L)

        assertEquals(3, rules.wipLimit)
        assertEquals(4, rules.teamSize)
        assertEquals(42L, rules.seedValue)
        assertEquals(rules.wipLimit, rules.policySet.wipLimit)
    }

    @Test
    fun `given invalid scenario rule limits when creating then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            ScenarioRules.create(wipLimit = 0, teamSize = 4, seedValue = 1L)
        }
        assertFailsWith<IllegalArgumentException> {
            ScenarioRules.create(wipLimit = 2, teamSize = 0, seedValue = 1L)
        }
    }

    @Test
    fun `given simulation when advancing and changing status then state transitions are reflected`() {
        val simulation = simulation()

        val running = simulation.withStatus(SimulationStatus.RUNNING)
        val nextDay = running.advanceDay()

        assertEquals(SimulationStatus.RUNNING, running.status)
        assertEquals(simulation.currentDay.value + 1, nextDay.currentDay.value)
    }

    @Test
    fun `given simulation when appending decision and snapshot then history grows consistently`() {
        val sim = simulation()
        val decision = Decision.MoveItem(cardId = CardId("card-1"))
        val snapshot =
            DailySnapshot(
                simulation = sim.id,
                scenario = sim.scenario.id,
                day = SimulationDay(1),
                metrics = FlowMetrics(throughput = 1, wipCount = 2, blockedCount = 0, avgAgingDays = 1.5),
                movements =
                    listOf(
                        Movement(type = MovementType.MOVED, cardId = CardId("card-1"), day = SimulationDay(1), reason = "manual"),
                    ),
            )

        val updated = sim.appendDecision(decision).appendSnapshot(snapshot)

        assertEquals(1, updated.decisions.size)
        assertEquals(1, updated.history.size)
        assertTrue(updated.decisions.first() is Decision.MoveItem)
    }

    @Test
    fun `given blank names for scenario and simulation when creating then validation fails`() {
        val organization = Organization.create(name = "Org")
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 3, seedValue = 7L)
        val board = Board.create(name = "Board")

        assertFailsWith<IllegalArgumentException> {
            Scenario.create(name = "", rules = rules, board = board)
        }
        assertFailsWith<IllegalArgumentException> {
            Simulation.create(name = "", organization = organization, scenario = Scenario.create("S", rules, board))
        }
    }

    private fun scenario(): Scenario {
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 3, seedValue = 99L)
        return Scenario.create(name = "Scenario", rules = rules, board = Board.create("Board"))
    }

    private fun simulation(): Simulation =
        Simulation.create(
            name = "Sim",
            organization = Organization.create(name = "Org"),
            scenario = scenario(),
            status = SimulationStatus.DRAFT,
        )
}
