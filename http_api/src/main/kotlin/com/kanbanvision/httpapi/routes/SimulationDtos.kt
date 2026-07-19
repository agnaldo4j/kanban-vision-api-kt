package com.kanbanvision.httpapi.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.common.errors.DomainError
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationError
import com.kanbanvision.usecases.Page
import com.kanbanvision.usecases.simulation.CfdResult
import kotlinx.serialization.Serializable
import java.util.Locale

internal const val EXAMPLE_ORGANIZATION_ID = "550e8400-e29b-41d4-a716-446655440000"
internal const val EXAMPLE_SIMULATION_ID = "550e8400-e29b-41d4-a716-446655440001"

@Serializable
data class CreateSimulationRequest(
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long = 0L,
) {
    // A organização é derivada do claim organizationId do JWT (tenancy), nunca do corpo (GAP-BJ).
    companion object {
        val example =
            CreateSimulationRequest(
                wipLimit = 5,
                teamSize = 4,
                seedValue = 12345L,
            )
    }
}

@Serializable
data class DecisionRequest(
    val type: String,
    val payload: Map<String, String> = emptyMap(),
) {
    companion object {
        val example = DecisionRequest(type = "MOVE_ITEM", payload = mapOf("cardId" to "card-1"))
    }
}

@Serializable
data class RunDayRequest(
    val decisions: List<DecisionRequest> = emptyList(),
) {
    companion object {
        val example = RunDayRequest(decisions = listOf(DecisionRequest.example))
    }
}

@Serializable
data class SimulationCreatedResponse(
    val simulationId: String,
) {
    companion object {
        val example = SimulationCreatedResponse(simulationId = EXAMPLE_SIMULATION_ID)
    }
}

@Serializable
data class SimulationStateResponse(
    val currentDay: Int,
    val wipLimit: Int,
    val teamSize: Int,
    val itemCount: Int,
) {
    companion object {
        val example = SimulationStateResponse(currentDay = 3, wipLimit = 5, teamSize = 4, itemCount = 7)
    }
}

@Serializable
data class SimulationResponse(
    val simulationId: String,
    val organizationId: String,
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long,
    val state: SimulationStateResponse,
) {
    companion object {
        val example =
            SimulationResponse(
                simulationId = EXAMPLE_SIMULATION_ID,
                organizationId = EXAMPLE_ORGANIZATION_ID,
                wipLimit = 5,
                teamSize = 4,
                seedValue = 12345L,
                state = SimulationStateResponse.example,
            )
    }
}

@Serializable
data class FlowMetricsResponse(
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
) {
    companion object {
        val example = FlowMetricsResponse(throughput = 2, wipCount = 4, blockedCount = 1, avgAgingDays = 2.5)
    }
}

@Serializable
data class MovementResponse(
    val type: String,
    val cardId: String,
    val day: Int,
    val reason: String,
) {
    companion object {
        val example =
            MovementResponse(
                type = "MOVED",
                cardId = "card-1",
                day = 3,
                reason = "puxado para a próxima etapa",
            )
    }
}

@Serializable
data class DailySnapshotResponse(
    val simulationId: String,
    val day: Int,
    val metrics: FlowMetricsResponse,
    val movements: List<MovementResponse>,
) {
    companion object {
        val example =
            DailySnapshotResponse(
                simulationId = EXAMPLE_SIMULATION_ID,
                day = 3,
                metrics = FlowMetricsResponse.example,
                movements = listOf(MovementResponse.example),
            )
    }
}

internal fun DailySnapshot.toResponse() =
    DailySnapshotResponse(
        simulationId = simulation.value,
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
                    cardId = m.cardId.value,
                    day = m.day.value,
                    reason = m.reason,
                )
            },
    )

@Serializable
data class SimulationSummaryResponse(
    val id: String,
    val name: String,
    val status: String,
    val currentDay: Int,
) {
    companion object {
        val example =
            SimulationSummaryResponse(
                id = EXAMPLE_SIMULATION_ID,
                name = "Simulação Time Alpha",
                status = "RUNNING",
                currentDay = 3,
            )
    }
}

@Serializable
data class SimulationListResponse(
    val data: List<SimulationSummaryResponse>,
    val page: Int,
    val size: Int,
    val total: Long,
) {
    companion object {
        val example =
            SimulationListResponse(
                data = listOf(SimulationSummaryResponse.example),
                page = 1,
                size = 20,
                total = 1L,
            )
    }
}

@Serializable
data class DayMetricsResponse(
    val day: Int,
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
) {
    companion object {
        val example = DayMetricsResponse(day = 1, throughput = 1, wipCount = 3, blockedCount = 0, avgAgingDays = 1.0)
    }
}

@Serializable
data class SimulationDaysResponse(
    val simulationId: String,
    val days: List<DayMetricsResponse>,
) {
    companion object {
        val example =
            SimulationDaysResponse(
                simulationId = EXAMPLE_SIMULATION_ID,
                days =
                    listOf(
                        DayMetricsResponse.example,
                        DayMetricsResponse(day = 2, throughput = 2, wipCount = 4, blockedCount = 1, avgAgingDays = 1.5),
                    ),
            )
    }
}

@Serializable
data class CfdDataPointResponse(
    val day: Int,
    val throughputCumulative: Int,
    val wipCount: Int,
    val blockedCount: Int,
) {
    companion object {
        val example = CfdDataPointResponse(day = 1, throughputCumulative = 1, wipCount = 3, blockedCount = 0)
    }
}

@Serializable
data class SimulationCfdResponse(
    val simulationId: String,
    val series: List<CfdDataPointResponse>,
) {
    companion object {
        val example =
            SimulationCfdResponse(
                simulationId = EXAMPLE_SIMULATION_ID,
                series =
                    listOf(
                        CfdDataPointResponse.example,
                        CfdDataPointResponse(day = 2, throughputCumulative = 3, wipCount = 4, blockedCount = 1),
                    ),
            )
    }
}

internal fun Page<Simulation>.toListResponse() =
    SimulationListResponse(
        data = data.map { it.toSummaryResponse() },
        page = page,
        size = size,
        total = total,
    )

internal fun Simulation.toSummaryResponse() =
    SimulationSummaryResponse(
        id = id.value,
        name = name,
        status = status.name,
        currentDay = currentDay.value,
    )

internal fun List<DailySnapshot>.toDaysResponse(simulationId: String) =
    SimulationDaysResponse(
        simulationId = simulationId,
        days =
            map { snapshot ->
                DayMetricsResponse(
                    day = snapshot.day.value,
                    throughput = snapshot.metrics.throughput,
                    wipCount = snapshot.metrics.wipCount,
                    blockedCount = snapshot.metrics.blockedCount,
                    avgAgingDays = snapshot.metrics.avgAgingDays,
                )
            },
    )

internal fun CfdResult.toResponse() =
    SimulationCfdResponse(
        simulationId = simulationId,
        series =
            series.map { point ->
                CfdDataPointResponse(
                    day = point.day,
                    throughputCumulative = point.throughputCumulative,
                    wipCount = point.wipCount,
                    blockedCount = point.blockedCount,
                )
            },
    )

internal fun DecisionRequest.toDomain(): Either<DomainError, Decision> =
    when (type.uppercase(Locale.ROOT)) {
        "MOVE_ITEM" -> toMoveDecision()
        "BLOCK_ITEM" -> toBlockDecision()
        "UNBLOCK_ITEM" -> toUnblockDecision()
        "ADD_ITEM" -> toAddDecision()
        else -> SimulationError.InvalidDecision("Unknown decision type: $type").left()
    }

// Valida ANTES de construir CardId: o guard isNotBlank do value class lançaria
// IllegalArgumentException (→ 500 via StatusPages) num cardId em branco. Aqui vira 400.
private fun DecisionRequest.requireCardId(type: String): Either<DomainError, CardId> =
    payload["cardId"]
        ?.takeIf { it.isNotBlank() }
        ?.let { CardId(it).right() }
        ?: SimulationError.InvalidDecision("Missing or blank required field 'cardId' for $type").left()

private fun DecisionRequest.toMoveDecision(): Either<DomainError, Decision.MoveItem> =
    requireCardId("MOVE_ITEM").map { Decision.MoveItem(it) }

private fun DecisionRequest.toBlockDecision(): Either<DomainError, Decision.BlockItem> =
    requireCardId("BLOCK_ITEM").map { Decision.BlockItem(it, payload["reason"] ?: "blocked") }

private fun DecisionRequest.toUnblockDecision(): Either<DomainError, Decision.UnblockItem> =
    requireCardId("UNBLOCK_ITEM").map { Decision.UnblockItem(it) }

private fun DecisionRequest.toAddDecision(): Either<DomainError, Decision.AddItem> =
    payload["title"]
        ?.let { Decision.AddItem(it, decisionServiceClass(payload["serviceClass"])).right() }
        ?: SimulationError.InvalidDecision("Missing required field 'title' for ADD_ITEM").left()

private fun decisionServiceClass(value: String?): ServiceClass =
    value?.let { runCatching { ServiceClass.valueOf(it) }.getOrNull() } ?: ServiceClass.STANDARD

internal fun Simulation.toSimulationResponse(): SimulationResponse {
    val cardCount = scenario.board.steps.sumOf { it.cards.size }
    return SimulationResponse(
        simulationId = id.value,
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
