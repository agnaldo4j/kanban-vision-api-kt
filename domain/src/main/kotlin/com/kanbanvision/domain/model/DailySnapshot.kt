package com.kanbanvision.domain.model

import java.util.UUID

data class DailySnapshot(
    override val id: String = UUID.randomUUID().toString(),
    val simulation: SimulationRef,
    val scenario: ScenarioRef,
    val day: SimulationDay,
    val metrics: FlowMetrics,
    val movements: List<Movement>,
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "DailySnapshot id must not be blank" }
    }

    val simulationId: String
        get() = simulation.id

    val scenarioId: String
        get() = scenario.id

    constructor(
        id: String = UUID.randomUUID().toString(),
        simulationId: String,
        scenarioId: String = "scenario-unknown",
        day: SimulationDay,
        metrics: FlowMetrics,
        movements: List<Movement>,
        audit: Audit = Audit(),
    ) : this(
        id = id,
        simulation = SimulationRef(simulationId),
        scenario = ScenarioRef(scenarioId),
        day = day,
        metrics = metrics,
        movements = movements,
        audit = audit,
    )
}
