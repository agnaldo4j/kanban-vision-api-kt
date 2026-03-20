package com.kanbanvision.domain.model

data class SimulationResult(
    val newState: SimulationState,
    val snapshot: DailySnapshot,
    val audit: Audit = Audit(),
)
