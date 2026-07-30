package com.kanbanvision.domain.model.simulation

@JvmInline
value class SimulationId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "SimulationId must not be blank" }
    }
}

@JvmInline
value class ScenarioId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ScenarioId must not be blank" }
    }
}
