package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.BoardId
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.StepId
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioId
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.domain.model.simulation.SimulationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReferenceObjectAndSnapshotCompatibilityBehaviorTest {
    @Test
    fun `given domain aggregates when converting to refs then identifiers are preserved`() {
        val board = Board.create(name = "Main").addStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
        val step = board.steps.first()
        val rules = ScenarioRules.create(wipLimit = 3, teamSize = 4, seedValue = 42)
        val scenario = Scenario.create(name = "Baseline", rules = rules, board = board)
        val organization = Organization.create(name = "Org")
        val simulation = Simulation.create(name = "Sim", organization = organization, scenario = scenario)

        assertEquals(board.id.value, board.toRef().value)
        assertEquals(step.id.value, step.toRef().value)
        assertEquals(scenario.id.value, scenario.toRef().value)
        assertEquals(simulation.id.value, simulation.toRef().value)
    }

    @Test
    fun `given blank ids when creating refs then validation fails`() {
        assertFailsWith<IllegalArgumentException> { BoardId("") }
        assertFailsWith<IllegalArgumentException> { StepId("") }
        assertFailsWith<IllegalArgumentException> { SimulationId("") }
        assertFailsWith<IllegalArgumentException> { ScenarioId("") }
    }

    @Test
    fun `given snapshots built from refs when created then referenced identifiers remain consistent`() {
        val metrics = FlowMetrics(throughput = 2, wipCount = 3, blockedCount = 1, avgAgingDays = 1.5)
        val movement = Movement(type = MovementType.MOVED, cardId = CardId("card-1"), day = SimulationDay(2), reason = "done")

        val fromRefs =
            DailySnapshot(
                simulation = SimulationId("sim-1"),
                scenario = ScenarioId("scn-1"),
                day = SimulationDay(2),
                metrics = metrics,
                movements = listOf(movement),
            )
        val fromIds =
            DailySnapshot(
                simulation = SimulationId("sim-2"),
                scenario = ScenarioId("scn-2"),
                day = SimulationDay(3),
                metrics = metrics,
                movements = emptyList(),
            )

        assertEquals("sim-1", fromRefs.simulation.value)
        assertEquals("scn-1", fromRefs.scenario.value)
        assertEquals("sim-2", fromIds.simulation.value)
        assertEquals("scn-2", fromIds.scenario.value)
    }

    @Test
    fun `given simulation result when audit is omitted then default audit is initialized`() {
        val board = Board.create(name = "Main")
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 2, seedValue = 7)
        val scenario = Scenario.create(name = "Flow", rules = rules, board = board)
        val organization = Organization.create(name = "Org")
        val simulation = Simulation.create(name = "Run", organization = organization, scenario = scenario)
        val snapshot =
            DailySnapshot(
                simulation = simulation.toRef(),
                scenario = scenario.toRef(),
                day = SimulationDay(1),
                metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0),
                movements = emptyList(),
            )

        val result = SimulationResult(simulation = simulation, snapshot = snapshot)

        assertTrue(result.audit.createdAt.isBefore(result.audit.updatedAt) || result.audit.createdAt == result.audit.updatedAt)
    }
}
