package com.kanbanvision.domain.model.valueobjects

import java.util.UUID

@JvmInline
value class ScenarioId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ScenarioId must not be blank" }
    }

    companion object {
        fun generate(): ScenarioId = ScenarioId(UUID.randomUUID().toString())
    }
}
