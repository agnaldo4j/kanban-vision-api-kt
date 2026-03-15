package com.kanbanvision.domain.model.scenario

import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.valueobjects.TenantId

data class Scenario(
    val id: ScenarioId,
    val tenantId: TenantId,
    val config: ScenarioConfig,
) {
    companion object {
        fun create(
            tenantId: TenantId,
            config: ScenarioConfig,
        ): Scenario = Scenario(id = ScenarioId.generate(), tenantId = tenantId, config = config)
    }
}
