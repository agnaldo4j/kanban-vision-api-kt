package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.Audit

data class SimulationResult(
    val simulation: Simulation,
    val snapshot: DailySnapshot,
    val audit: Audit = Audit(),
)
