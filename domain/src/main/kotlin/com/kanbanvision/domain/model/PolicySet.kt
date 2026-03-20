package com.kanbanvision.domain.model

data class PolicySet(
    val wipLimit: Int,
    val audit: Audit = Audit(),
) {
    init {
        require(wipLimit > 0) { "WIP limit must be greater than zero" }
    }

    companion object {
        fun from(config: ScenarioConfig): PolicySet = PolicySet(wipLimit = config.wipLimit)
    }
}
