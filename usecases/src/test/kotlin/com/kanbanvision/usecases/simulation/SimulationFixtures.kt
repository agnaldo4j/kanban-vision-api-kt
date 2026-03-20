package com.kanbanvision.usecases.simulation

import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationStatus

internal fun fixtureOrganization(
    id: String = "org-1",
    name: String = "Organization",
): Organization = Organization(id = id, name = name)

internal fun fixtureScenario(
    id: String = "scn-1",
    wipLimit: Int = 2,
    teamSize: Int = 2,
    seedValue: Long = 42L,
): Scenario {
    val board =
        Board(id = "board-1", name = "Main Board")
            .addStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
            .addStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)
    return Scenario(
        id = id,
        name = "Default Simulation Scenario",
        rules = ScenarioRules.create(wipLimit = wipLimit, teamSize = teamSize, seedValue = seedValue),
        board = board,
    )
}

internal fun fixtureSimulation(
    id: String = "sim-1",
    day: Int = 1,
    status: SimulationStatus = SimulationStatus.DRAFT,
): Simulation =
    Simulation(
        id = id,
        name = "Simulation",
        currentDay = SimulationDay(day),
        status = status,
        organization = fixtureOrganization(),
        scenario = fixtureScenario(),
    )

internal fun fixtureSnapshot(
    simulationId: String = "sim-1",
    day: Int = 1,
): DailySnapshot =
    DailySnapshot(
        simulationId = simulationId,
        day = SimulationDay(day),
        metrics = FlowMetrics(throughput = 1, wipCount = 1, blockedCount = 0, avgAgingDays = 0.0),
        movements = emptyList(),
    )
