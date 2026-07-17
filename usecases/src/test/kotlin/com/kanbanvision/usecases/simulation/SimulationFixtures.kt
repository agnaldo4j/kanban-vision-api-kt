package com.kanbanvision.usecases.simulation

import com.kanbanvision.domain.model.BoardId
import com.kanbanvision.domain.model.CardId
import com.kanbanvision.domain.model.ScenarioId
import com.kanbanvision.domain.model.SimulationId
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationStatus

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
        Board(id = BoardId("board-1"), name = "Main Board")
            .addStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
            .addStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)
    return Scenario(
        id = ScenarioId(id),
        name = "Default Simulation Scenario",
        rules = ScenarioRules.create(wipLimit = wipLimit, teamSize = teamSize, seedValue = seedValue),
        board = board,
    )
}

internal const val FIXTURE_ORGANIZATION_ID = "org-1"

internal fun fixtureSimulation(
    id: String = "sim-1",
    day: Int = 1,
    status: SimulationStatus = SimulationStatus.DRAFT,
    organizationId: String = FIXTURE_ORGANIZATION_ID,
): Simulation =
    Simulation(
        id = SimulationId(id),
        name = "Simulation",
        currentDay = SimulationDay(day),
        status = status,
        organization = fixtureOrganization(id = organizationId),
        scenario = fixtureScenario(),
    )

internal fun fixtureSnapshot(
    simulationId: String = "sim-1",
    scenarioId: String = "scn-1",
    day: Int = 1,
): DailySnapshot =
    DailySnapshot(
        simulation = SimulationId(simulationId),
        scenario = ScenarioId(scenarioId),
        day = SimulationDay(day),
        metrics = FlowMetrics(throughput = 1, wipCount = 1, blockedCount = 0, avgAgingDays = 0.0),
        movements = emptyList(),
    )

internal fun fixtureSnapshotWithAllMovements(
    simulationId: String = "sim-1",
    day: Int = 1,
): DailySnapshot =
    fixtureSnapshot(simulationId = simulationId, day = day).copy(
        movements =
            listOf(
                Movement(type = MovementType.MOVED, cardId = CardId("c-1"), day = SimulationDay(day), reason = "moved"),
                Movement(type = MovementType.BLOCKED, cardId = CardId("c-2"), day = SimulationDay(day), reason = "dep"),
                Movement(type = MovementType.UNBLOCKED, cardId = CardId("c-3"), day = SimulationDay(day), reason = "resumed"),
                Movement(type = MovementType.COMPLETED, cardId = CardId("c-4"), day = SimulationDay(day), reason = "done"),
            ),
    )
