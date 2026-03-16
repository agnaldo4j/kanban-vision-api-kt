package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.policy.PolicySet
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.WorkItemId
import com.kanbanvision.domain.model.workitem.ServiceClass
import com.kanbanvision.domain.model.workitem.WorkItem
import com.kanbanvision.domain.model.workitem.WorkItemState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class WorkItemSurrogate(
    val id: String,
    val title: String,
    val serviceClass: String,
    val state: String,
    val agingDays: Int,
)

@Serializable
private data class SimulationStateSurrogate(
    val currentDay: Int,
    val wipLimit: Int,
    val items: List<WorkItemSurrogate>,
)

internal object SimulationStateSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: SimulationState): String = json.encodeToString(state.toSurrogate())

    fun decode(raw: String): SimulationState = json.decodeFromString<SimulationStateSurrogate>(raw).toDomain()

    private fun SimulationState.toSurrogate() =
        SimulationStateSurrogate(
            currentDay = currentDay.value,
            wipLimit = policySet.wipLimit,
            items = items.map { it.toSurrogate() },
        )

    private fun WorkItem.toSurrogate() =
        WorkItemSurrogate(
            id = id.value,
            title = title,
            serviceClass = serviceClass.name,
            state = state.name,
            agingDays = agingDays,
        )

    private fun SimulationStateSurrogate.toDomain() =
        SimulationState(
            currentDay = SimulationDay(currentDay),
            policySet = PolicySet(wipLimit = wipLimit),
            items = items.map { it.toDomain() },
        )

    private fun WorkItemSurrogate.toDomain() =
        WorkItem(
            id = WorkItemId(id),
            title = title,
            serviceClass = ServiceClass.valueOf(serviceClass),
            state = WorkItemState.valueOf(state),
            agingDays = agingDays,
        )
}
