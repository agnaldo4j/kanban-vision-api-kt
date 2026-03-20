package com.kanbanvision.domain.model

data class SimulationResult(
    val simulation: Simulation,
    val snapshot: DailySnapshot,
    val audit: Audit = Audit(),
)
