package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.ScenarioId
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationId

internal fun Decision.toSurrogate(): DecisionSurrogate =
    when (this) {
        is Decision.MoveItem -> DecisionSurrogate(type = "MOVE_ITEM", payload = mapOf("cardId" to cardId.value))
        is Decision.BlockItem -> DecisionSurrogate(type = "BLOCK_ITEM", payload = mapOf("cardId" to cardId.value, "reason" to reason))
        is Decision.UnblockItem -> DecisionSurrogate(type = "UNBLOCK_ITEM", payload = mapOf("cardId" to cardId.value))
        is Decision.AddItem ->
            DecisionSurrogate(
                type = "ADD_ITEM",
                payload =
                    mapOf(
                        "title" to title.value,
                        "serviceClass" to serviceClass.name,
                    ),
            )
    }

internal fun DecisionSurrogate.toDomain(): Decision =
    when (type) {
        "MOVE_ITEM" -> Decision.MoveItem(cardId = CardId(payload.need("cardId")))
        "BLOCK_ITEM" -> Decision.BlockItem(cardId = CardId(payload.need("cardId")), reason = payload["reason"] ?: "blocked")
        "UNBLOCK_ITEM" -> Decision.UnblockItem(cardId = CardId(payload.need("cardId")))
        "ADD_ITEM" -> Decision.AddItem(title = NonBlankTitle(payload.need("title")), serviceClass = surrogateServiceClass(payload))
        else -> error("Unknown decision type: $type")
    }

private fun Map<String, String>.need(key: String): String = this[key] ?: error("Decision payload missing '$key'")

private fun surrogateServiceClass(payload: Map<String, String>): ServiceClass =
    payload["serviceClass"]?.let { runCatching { ServiceClass.valueOf(it) }.getOrNull() } ?: ServiceClass.STANDARD

internal fun DailySnapshot.toSurrogate() =
    DailySnapshotSurrogate(
        id = id,
        simulationId = simulation.value,
        scenarioId = scenario.value,
        day = day.value,
        metrics = metrics.toSurrogate(),
        movements = movements.map { it.toSurrogate() },
    )

internal fun DailySnapshotSurrogate.toDomain() =
    DailySnapshot(
        id = id,
        simulation = SimulationId(simulationId),
        scenario = ScenarioId(scenarioId),
        day = SimulationDay(day),
        metrics = metrics.toDomain(),
        movements = movements.map { it.toDomain() },
    )

private fun FlowMetrics.toSurrogate() =
    FlowMetricsSurrogate(
        id = id,
        throughput = throughput,
        wipCount = wipCount,
        blockedCount = blockedCount,
        avgAgingDays = avgAgingDays,
    )

private fun FlowMetricsSurrogate.toDomain() =
    FlowMetrics(
        id = id,
        throughput = throughput,
        wipCount = wipCount,
        blockedCount = blockedCount,
        avgAgingDays = avgAgingDays,
    )

private fun Movement.toSurrogate() =
    MovementSurrogate(
        id = id,
        type = type.name,
        cardId = cardId.value,
        day = day.value,
        reason = reason,
    )

private fun MovementSurrogate.toDomain() =
    Movement(
        id = id,
        type = MovementType.valueOf(type),
        cardId = CardId(cardId),
        day = SimulationDay(day),
        reason = reason,
    )
