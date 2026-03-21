package com.kanbanvision.domain.model

import java.util.UUID

data class DailySnapshot(
    override val id: String = UUID.randomUUID().toString(),
    val simulationId: String,
    val day: SimulationDay,
    val metrics: FlowMetrics,
    val movements: List<Movement>,
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "DailySnapshot id must not be blank" }
        require(simulationId.isNotBlank()) { "DailySnapshot simulationId must not be blank" }
    }
}
