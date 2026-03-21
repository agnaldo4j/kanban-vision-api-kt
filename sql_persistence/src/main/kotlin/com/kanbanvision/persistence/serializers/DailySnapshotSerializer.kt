package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.ScenarioRef
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class SnapshotFlowMetricsSurrogate(
    val id: String,
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
)

@Serializable
private data class SnapshotMovementSurrogate(
    val id: String,
    val type: String,
    val cardId: String,
    val day: Int,
    val reason: String,
)

@Serializable
private data class SnapshotDailySnapshotSurrogate(
    val id: String,
    val simulationId: String,
    val scenarioId: String,
    val day: Int,
    val metrics: SnapshotFlowMetricsSurrogate,
    val movements: List<SnapshotMovementSurrogate>,
)

internal object DailySnapshotSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: DailySnapshot): String = json.encodeToString(snapshot.toSurrogate())

    fun decode(raw: String): DailySnapshot = json.decodeFromString<SnapshotDailySnapshotSurrogate>(raw).toDomain()

    private fun DailySnapshot.toSurrogate() =
        SnapshotDailySnapshotSurrogate(
            id = id,
            simulationId = simulation.id,
            scenarioId = scenario.id,
            day = day.value,
            metrics = metrics.toSurrogate(),
            movements = movements.map { it.toSurrogate() },
        )

    private fun FlowMetrics.toSurrogate() =
        SnapshotFlowMetricsSurrogate(
            id = id,
            throughput = throughput,
            wipCount = wipCount,
            blockedCount = blockedCount,
            avgAgingDays = avgAgingDays,
        )

    private fun Movement.toSurrogate() =
        SnapshotMovementSurrogate(
            id = id,
            type = type.name,
            cardId = cardId,
            day = day.value,
            reason = reason,
        )

    private fun SnapshotDailySnapshotSurrogate.toDomain() =
        DailySnapshot(
            id = id,
            simulation = SimulationRef(simulationId),
            scenario = ScenarioRef(scenarioId),
            day = SimulationDay(day),
            metrics = metrics.toDomain(),
            movements = movements.map { it.toDomain() },
        )

    private fun SnapshotFlowMetricsSurrogate.toDomain() =
        FlowMetrics(
            id = id,
            throughput = throughput,
            wipCount = wipCount,
            blockedCount = blockedCount,
            avgAgingDays = avgAgingDays,
        )

    private fun SnapshotMovementSurrogate.toDomain() =
        Movement(
            id = id,
            type = MovementType.valueOf(type),
            cardId = cardId,
            day = SimulationDay(day),
            reason = reason,
        )
}
