package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Domain
import java.util.UUID

data class DailySnapshot(
    override val id: String = UUID.randomUUID().toString(),
    val simulation: SimulationId,
    val scenario: ScenarioId,
    val day: SimulationDay,
    val metrics: FlowMetrics,
    val movements: List<Movement>,
    override val audit: Audit = Audit(),
) : Domain<String> {
    init {
        require(id.isNotBlank()) { "DailySnapshot id must not be blank" }
    }
}
