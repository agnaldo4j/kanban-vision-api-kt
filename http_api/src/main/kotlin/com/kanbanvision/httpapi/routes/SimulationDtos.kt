package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.Simulation
import kotlinx.serialization.Serializable

@Serializable
data class CreateSimulationRequest(
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
data class SimulationCreatedResponse(
    val simulationId: String,
)

@Serializable
data class SimulationStateResponse(
    val currentDay: Int,
    val wipLimit: Int,
    val teamSize: Int,
    val itemCount: Int,
)

@Serializable
data class SimulationResponse(
    val simulationId: String,
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
    val simulationId: String,
    val day: Int,
    val metrics: FlowMetricsResponse,
    val movements: List<MovementResponse>,
)

internal fun DailySnapshot.toResponse() =
    DailySnapshotResponse(
        simulationId = simulation.id,
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

internal fun Simulation.toSimulationResponse(): SimulationResponse {
    val cardCount = scenario.board.steps.sumOf { it.cards.size }
    return SimulationResponse(
        simulationId = id,
        organizationId = organization.id,
        wipLimit = scenario.rules.wipLimit,
        teamSize = scenario.rules.teamSize,
        seedValue = scenario.rules.seedValue,
        state =
            SimulationStateResponse(
                currentDay = currentDay.value,
                wipLimit = scenario.rules.policySet.wipLimit,
                teamSize = scenario.rules.teamSize,
                itemCount = cardCount,
            ),
    )
}
