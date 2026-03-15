package com.kanbanvision.domain.model.policy

import com.kanbanvision.domain.model.scenario.ScenarioConfig

data class PolicySet(
    val wipLimit: Int,
) {
    init {
        require(wipLimit > 0) { "WIP limit must be greater than zero" }
    }

    companion object {
        fun from(config: ScenarioConfig): PolicySet = PolicySet(wipLimit = config.wipLimit)
    }
}
