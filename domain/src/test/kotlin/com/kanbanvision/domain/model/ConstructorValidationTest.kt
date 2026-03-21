package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ConstructorValidationTest {
    @Test
    fun `given ability with blank id when constructing then validation fails`() {
        assertFailsWith<IllegalArgumentException> { Ability(id = "", name = AbilityName.DEVELOPER, seniority = Seniority.PL) }
    }

    @Test
    fun `given organization with blank id or name when constructing then validation fails`() {
        assertFailsWith<IllegalArgumentException> { Organization(id = "", name = "Org") }
        assertFailsWith<IllegalArgumentException> { Organization(id = "org-1", name = "") }
    }

    @Test
    fun `given scenario rules with invalid limits when constructing then validation fails`() {
        val policy = PolicySet(wipLimit = 1)
        assertFailsWith<IllegalArgumentException> {
            ScenarioRules(id = "", policySet = policy, wipLimit = 1, teamSize = 1, seedValue = 1L)
        }
        assertFailsWith<IllegalArgumentException> {
            ScenarioRules(policySet = policy, wipLimit = 0, teamSize = 1, seedValue = 1L)
        }
        assertFailsWith<IllegalArgumentException> {
            ScenarioRules(policySet = policy, wipLimit = 1, teamSize = 0, seedValue = 1L)
        }
    }

    @Test
    fun `given flow metrics and policy set with invalid values when constructing then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(id = "", throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
        }
        assertFailsWith<IllegalArgumentException> { PolicySet(id = "", wipLimit = 1) }
        assertFailsWith<IllegalArgumentException> { PolicySet(id = "p-1", wipLimit = 0) }
    }

    @Test
    fun `given movement and snapshot with blank identifiers when constructing then validation fails`() {
        val metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
        assertFailsWith<IllegalArgumentException> {
            Movement(id = "", type = MovementType.MOVED, cardId = "c-1", day = SimulationDay(1), reason = "x")
        }
        assertFailsWith<IllegalArgumentException> {
            DailySnapshot(
                id = "",
                simulationId = "sim-1",
                day = SimulationDay(1),
                metrics = metrics,
                movements = emptyList(),
            )
        }
    }

    @Test
    fun `given board or step with invalid fields when constructing then validation fails`() {
        assertFailsWith<IllegalArgumentException> { Board(id = "", name = "B") }
        assertFailsWith<IllegalArgumentException> { Board(id = "b-1", name = "") }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "", boardId = "b-1", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "s-1", boardId = "", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "s-1", boardId = "b-1", name = "", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "s-1", boardId = "b-1", name = "Dev", position = -1, requiredAbility = AbilityName.DEVELOPER)
        }
    }

    @Test
    fun `given scenario with invalid identifiers or name when constructing then validation fails`() {
        val rules = ScenarioRules.create(1, 1, 1L)
        val board = Board.create("Board")

        assertFailsWith<IllegalArgumentException> {
            Scenario(id = "", name = "Scenario", rules = rules, board = board)
        }
        assertFailsWith<IllegalArgumentException> {
            Scenario(id = "scn-1", name = "", rules = rules, board = board)
        }
    }

    @Test
    fun `given simulation with invalid identifiers or name when constructing then validation fails`() {
        val rules = ScenarioRules.create(1, 1, 1L)
        val board = Board.create("Board")
        val org = Organization.create("Org")
        val scenario = Scenario.create("Scenario", rules, board)

        assertFailsWith<IllegalArgumentException> {
            Simulation(
                id = "",
                name = "Sim",
                currentDay = SimulationDay(1),
                status = SimulationStatus.DRAFT,
                organization = org,
                scenario = scenario,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Simulation(
                id = "sim-1",
                name = "",
                currentDay = SimulationDay(1),
                status = SimulationStatus.DRAFT,
                organization = org,
                scenario = scenario,
            )
        }
    }
}
