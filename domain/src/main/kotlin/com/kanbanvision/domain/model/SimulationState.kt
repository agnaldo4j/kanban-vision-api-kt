package com.kanbanvision.domain.model

data class SimulationState(
    val currentDay: SimulationDay,
    val cards: List<Card>,
    val policySet: PolicySet,
    val context: SimulationContext? = null,
    val audit: Audit = Audit(),
) {
    companion object {
        fun initial(config: ScenarioConfig): SimulationState =
            SimulationState(
                currentDay = SimulationDay(1),
                cards = emptyList(),
                policySet = PolicySet.from(config),
            )

        fun initial(
            scenario: Scenario,
            config: ScenarioConfig,
            tribes: List<Tribe> = emptyList(),
        ): SimulationState =
            SimulationState(
                currentDay = SimulationDay(1),
                cards = emptyList(),
                policySet = PolicySet.from(config),
                context =
                    SimulationContext(
                        organizationId = scenario.organizationId,
                        boardId = scenario.boardId,
                        tribes = tribes,
                    ),
            )
    }
}
