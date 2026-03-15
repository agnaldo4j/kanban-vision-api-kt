package com.kanbanvision.domain.model.scenario

data class ScenarioConfig(
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long,
) {
    init {
        require(wipLimit > 0) { "WIP limit must be greater than zero" }
        require(teamSize > 0) { "Team size must be greater than zero" }
    }
}
