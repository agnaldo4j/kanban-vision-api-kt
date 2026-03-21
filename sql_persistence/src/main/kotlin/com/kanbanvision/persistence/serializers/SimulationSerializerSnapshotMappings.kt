package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.DecisionType
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.ScenarioRef
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationRef

internal fun Decision.toSurrogate() = DecisionSurrogate(id = id, type = type.name, payload = payload)

internal fun DecisionSurrogate.toDomain() = Decision(id = id, type = DecisionType.valueOf(type), payload = payload)

internal fun DailySnapshot.toSurrogate() =
    DailySnapshotSurrogate(
        id = id,
        simulationId = simulation.id,
        scenarioId = scenario.id,
        day = day.value,
        metrics = metrics.toSurrogate(),
        movements = movements.map { it.toSurrogate() },
    )

internal fun DailySnapshotSurrogate.toDomain() =
    DailySnapshot(
        id = id,
        simulation = SimulationRef(simulationId),
        scenario = ScenarioRef(scenarioId),
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
        cardId = cardId,
        day = day.value,
        reason = reason,
    )

private fun MovementSurrogate.toDomain() =
    Movement(
        id = id,
        type = MovementType.valueOf(type),
        cardId = cardId,
        day = SimulationDay(day),
        reason = reason,
    )
