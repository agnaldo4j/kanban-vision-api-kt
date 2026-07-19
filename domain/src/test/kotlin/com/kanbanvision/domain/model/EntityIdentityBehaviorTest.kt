package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.BoardId
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioId
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.domain.model.simulation.SimulationStatus
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EntityIdentityBehaviorTest {
    @Test
    fun `given blank ids in domain entities when constructing then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> { Organization(id = "", name = "Org") }
        assertFailsWith<IllegalArgumentException> { Board(id = BoardId(""), name = "Board") }
        assertFailsWith<IllegalArgumentException> {
            Scenario(
                id = ScenarioId(""),
                name = "Scenario",
                rules = scenarioRules(),
                board = board(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Simulation(
                id = SimulationId(""),
                name = "Simulation",
                currentDay = SimulationDay(1),
                status = SimulationStatus.DRAFT,
                organization = organization(),
                scenario = scenario(),
            )
        }
    }

    @Test
    fun `given blank names in domain entities when constructing with valid id then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> { Board(id = BoardId("b-1"), name = "") }
        assertFailsWith<IllegalArgumentException> { Scenario(id = ScenarioId("sc-1"), name = "", rules = scenarioRules(), board = board()) }
        assertFailsWith<IllegalArgumentException> {
            Simulation(
                id = SimulationId("s-1"),
                name = "",
                currentDay = SimulationDay(1),
                status = SimulationStatus.DRAFT,
                organization = organization(),
                scenario = scenario(),
            )
        }
    }

    private fun organization(): Organization = Organization.create(name = "Org")

    private fun board(): Board = Board.create(name = "Board")

    private fun scenarioRules(): ScenarioRules = ScenarioRules.create(wipLimit = 2, teamSize = 3, seedValue = 42L)

    private fun scenario(): Scenario = Scenario.create(name = "Scenario", rules = scenarioRules(), board = board())
}
