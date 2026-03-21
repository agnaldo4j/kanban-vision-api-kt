package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AggregateAndCompanionValidationBehaviorTest {
    @Test
    fun `given blank names when creating top level aggregates then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> { Organization.create(name = "") }
        assertFailsWith<IllegalArgumentException> { Board.create(name = "") }
        assertFailsWith<IllegalArgumentException> { Tribe(name = "") }
        assertFailsWith<IllegalArgumentException> { Squad(name = "") }
    }

    @Test
    fun `given board add card with unknown step when adding then operation fails fast`() {
        val board = Board.create(name = "Board").addStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)

        assertFailsWith<IllegalStateException> {
            board.addCard(stepId = "missing", title = "Task")
        }
    }

    @Test
    fun `given ability and policy set when creating with invalid values then constructors reject invalid state`() {
        assertFailsWith<IllegalArgumentException> {
            Ability(id = "", name = AbilityName.DEVELOPER, seniority = Seniority.JR)
        }
        assertFailsWith<IllegalArgumentException> {
            PolicySet(wipLimit = 0)
        }
    }

    @Test
    fun `given scenario rules mismatching policy set when constructing then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            ScenarioRules(
                policySet = PolicySet(wipLimit = 2),
                wipLimit = 3,
                teamSize = 4,
                seedValue = 10L,
            )
        }
    }

    @Test
    fun `given simulation status and result when creating then data is preserved`() {
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 2, seedValue = 1L)
        val board = Board.create(name = "Board")
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = board)
        val simulation =
            Simulation.create(
                name = "Simulation",
                organization = Organization.create(name = "Org"),
                scenario = scenario,
                status = SimulationStatus.PAUSED,
            )
        val snapshot =
            DailySnapshot(
                simulationId = simulation.id,
                day = SimulationDay(1),
                metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0),
                movements = emptyList(),
            )

        val result = SimulationResult(simulation = simulation, snapshot = snapshot)

        assertEquals(SimulationStatus.PAUSED, result.simulation.status)
        assertEquals(simulation.id, result.snapshot.simulationId)
    }
}
