package com.kanbanvision.domain.model.scenario

data class SimulationResult(
    val newState: SimulationState,
    val snapshot: DailySnapshot,
)
