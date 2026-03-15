package com.kanbanvision.domain.model.scenario

import com.kanbanvision.domain.model.metrics.FlowMetrics
import com.kanbanvision.domain.model.movement.Movement
import com.kanbanvision.domain.model.valueobjects.ScenarioId

data class DailySnapshot(
    val scenarioId: ScenarioId,
    val day: SimulationDay,
    val metrics: FlowMetrics,
    val movements: List<Movement>,
)
