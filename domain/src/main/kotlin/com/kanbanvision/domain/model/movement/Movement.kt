package com.kanbanvision.domain.model.movement

import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.valueobjects.WorkItemId

data class Movement(
    val type: MovementType,
    val workItemId: WorkItemId,
    val day: SimulationDay,
    val reason: String,
)
