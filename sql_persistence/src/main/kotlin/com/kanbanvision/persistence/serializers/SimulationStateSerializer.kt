package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.PolicySet
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.domain.model.WorkItemState
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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
    val cards: List<WorkItemSurrogate> = emptyList(),
    val items: List<WorkItemSurrogate> = emptyList(),
)

internal object SimulationStateSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: SimulationState): String = json.encodeToString(state.toSurrogate())

    fun decode(raw: String): SimulationState = json.decodeFromString<SimulationStateSurrogate>(raw).toDomain()

    private fun SimulationState.toSurrogate() =
        SimulationStateSurrogate(
            currentDay = currentDay.value,
            wipLimit = policySet.wipLimit,
            cards = cards.map { it.toSurrogate() },
        )

    private fun Card.toSurrogate() =
        WorkItemSurrogate(
            id = id,
            title = title,
            serviceClass = serviceClass.name,
            state = state.name,
            agingDays = agingDays,
        )

    private fun SimulationStateSurrogate.toDomain() =
        SimulationState(
            currentDay = SimulationDay(currentDay),
            policySet = PolicySet(wipLimit = wipLimit),
            cards = (cards.ifEmpty { items }).map { it.toDomain() },
        )

    private fun WorkItemSurrogate.toDomain() =
        Card(
            id = id,
            title = title,
            serviceClass = ServiceClass.valueOf(serviceClass),
            state = WorkItemState.valueOf(state),
            agingDays = agingDays,
        )
}
