package com.kanbanvision.domain.model.scenario

import com.kanbanvision.domain.model.policy.PolicySet
import com.kanbanvision.domain.model.workitem.WorkItem

data class SimulationState(
    val currentDay: SimulationDay,
    val items: List<WorkItem>,
    val policySet: PolicySet,
) {
    companion object {
        fun initial(config: ScenarioConfig): SimulationState =
            SimulationState(
                currentDay = SimulationDay(1),
                items = emptyList(),
                policySet = PolicySet.from(config),
            )
    }
}
