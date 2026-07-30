package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.ScenarioId
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
        is Decision.Unknown -> DecisionSurrogate(type = type, payload = payload)
    }

internal fun DecisionSurrogate.toDomain(): Decision =
    when (type) {
        "MOVE_ITEM", "BLOCK_ITEM", "UNBLOCK_ITEM" -> cardIdDecision()
        "ADD_ITEM" -> Decision.AddItem(title = decodeTitle(payload["title"].orEmpty()), serviceClass = surrogateServiceClass(payload))
        else -> unknown()
    }

private fun DecisionSurrogate.cardIdDecision(): Decision {
    val raw = payload["cardId"]?.takeIf { it.isNotBlank() } ?: return unknown()
    return when (type) {
        "MOVE_ITEM" -> Decision.MoveItem(cardId = CardId(raw))
        "BLOCK_ITEM" -> Decision.BlockItem(cardId = CardId(raw), reason = payload["reason"] ?: "blocked")
        else -> Decision.UnblockItem(cardId = CardId(raw))
    }
}

private fun DecisionSurrogate.unknown(): Decision.Unknown = Decision.Unknown(type = type, payload = payload)

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
        id = decodeId(id),
        simulation = SimulationId(decodeId(simulationId)),
        scenario = ScenarioId(decodeId(scenarioId)),
        day = decodeDay(day),
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
        id = decodeId(id),
        throughput = throughput.coerceAtLeast(0),
        wipCount = wipCount.coerceAtLeast(0),
        blockedCount = blockedCount.coerceAtLeast(0),
        avgAgingDays = avgAgingDays.coerceAtLeast(0.0),
    )

private fun Movement.toSurrogate() =
    MovementSurrogate(
        id = id,
        type = type.tag,
        cardId = cardId.value,
        day = day.value,
        reason = reason,
    )

private fun MovementSurrogate.toDomain() =
    Movement(
        id = decodeId(id),
        type = MovementType.fromTag(type),
        cardId = decodeCardId(cardId),
        day = decodeDay(day),
        reason = reason,
    )
