package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.model.DailySnapshot
import kotlinx.serialization.Serializable

@Serializable
data class CreateScenarioRequest(
    val organizationId: String,
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long = 0L,
)

@Serializable
data class DecisionRequest(
    val type: String,
    val payload: Map<String, String> = emptyMap(),
)

@Serializable
data class RunDayRequest(
    val decisions: List<DecisionRequest> = emptyList(),
)

@Serializable
data class ScenarioCreatedResponse(
    val scenarioId: String,
)

@Serializable
data class SimulationStateResponse(
    val currentDay: Int,
    val wipLimit: Int,
    val teamSize: Int,
    val itemCount: Int,
)

@Serializable
data class ScenarioResponse(
    val scenarioId: String,
    val organizationId: String,
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long,
    val state: SimulationStateResponse,
)

@Serializable
data class FlowMetricsResponse(
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
)

@Serializable
data class MovementResponse(
    val type: String,
    val cardId: String,
    val day: Int,
    val reason: String,
)

@Serializable
data class DailySnapshotResponse(
    val scenarioId: String,
    val day: Int,
    val metrics: FlowMetricsResponse,
    val movements: List<MovementResponse>,
)

internal fun DailySnapshot.toResponse() =
    DailySnapshotResponse(
        scenarioId = scenarioId,
        day = day.value,
        metrics =
            FlowMetricsResponse(
                throughput = metrics.throughput,
                wipCount = metrics.wipCount,
                blockedCount = metrics.blockedCount,
                avgAgingDays = metrics.avgAgingDays,
            ),
        movements =
            movements.map { m ->
                MovementResponse(
                    type = m.type.name,
                    cardId = m.cardId,
                    day = m.day.value,
                    reason = m.reason,
                )
            },
    )
