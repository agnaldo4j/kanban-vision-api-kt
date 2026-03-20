package com.kanbanvision.domain.model

data class SimulationState(
    val currentDay: SimulationDay,
    val cards: List<Card>,
    val policySet: PolicySet,
    val audit: Audit = Audit(),
) {
    companion object {
        fun initial(config: ScenarioConfig): SimulationState =
            SimulationState(
                currentDay = SimulationDay(1),
                cards = emptyList(),
                policySet = PolicySet.from(config),
            )
    }
}
