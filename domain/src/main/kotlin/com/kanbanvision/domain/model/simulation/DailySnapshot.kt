package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Domain
import com.kanbanvision.domain.model.ScenarioRef
import com.kanbanvision.domain.model.SimulationRef
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
}
