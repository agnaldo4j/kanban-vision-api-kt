package com.kanbanvision.domain.model

data class DailySnapshot(
    val scenarioId: String,
    val day: SimulationDay,
    val metrics: FlowMetrics,
    val movements: List<Movement>,
    val audit: Audit = Audit(),
) {
    init {
        require(scenarioId.isNotBlank()) { "DailySnapshot scenarioId must not be blank" }
    }
}
