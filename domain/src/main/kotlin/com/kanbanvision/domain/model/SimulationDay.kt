package com.kanbanvision.domain.model

@JvmInline
value class SimulationDay(
    val value: Int,
) {
    init {
        require(value >= 1) { "SimulationDay must be at least 1" }
    }
}
