package com.kanbanvision.domain.model

import java.util.UUID

data class Scenario(
    val id: String,
    val tenantId: String,
    val config: ScenarioConfig,
    val audit: Audit = Audit(),
) {
    /**
     * A simulation scenario represents a specific Kanban board context.
     * We currently bind it 1:1 to the scenario identifier.
     */
    val boardId: String get() = id

    init {
        require(id.isNotBlank()) { "Scenario id must not be blank" }
        require(tenantId.isNotBlank()) { "Scenario tenantId must not be blank" }
    }

    companion object {
        fun create(
            tenantId: String,
            config: ScenarioConfig,
        ): Scenario = Scenario(id = UUID.randomUUID().toString(), tenantId = tenantId, config = config)
    }
}
