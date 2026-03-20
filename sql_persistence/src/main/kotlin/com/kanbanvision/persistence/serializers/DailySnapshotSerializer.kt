package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.SimulationDay
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class FlowMetricsSurrogate(
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
)

@Serializable
private data class MovementSurrogate(
    val type: String,
    val cardId: String,
    val day: Int,
    val reason: String,
)

@Serializable
private data class DailySnapshotSurrogate(
    val scenarioId: String,
    val day: Int,
    val metrics: FlowMetricsSurrogate,
    val movements: List<MovementSurrogate>,
)

internal object DailySnapshotSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: DailySnapshot): String = json.encodeToString(snapshot.toSurrogate())

    fun decode(raw: String): DailySnapshot = json.decodeFromString<DailySnapshotSurrogate>(raw).toDomain()

    private fun DailySnapshot.toSurrogate() =
        DailySnapshotSurrogate(
            scenarioId = scenarioId,
            day = day.value,
            metrics = metrics.toSurrogate(),
            movements = movements.map { it.toSurrogate() },
        )

    private fun FlowMetrics.toSurrogate() = FlowMetricsSurrogate(throughput, wipCount, blockedCount, avgAgingDays)

    private fun Movement.toSurrogate() = MovementSurrogate(type = type.name, cardId = cardId, day = day.value, reason = reason)

    private fun DailySnapshotSurrogate.toDomain() =
        DailySnapshot(
            scenarioId = scenarioId,
            day = SimulationDay(day),
            metrics = metrics.toDomain(),
            movements = movements.map { it.toDomain() },
        )

    private fun FlowMetricsSurrogate.toDomain() = FlowMetrics(throughput, wipCount, blockedCount, avgAgingDays)

    private fun MovementSurrogate.toDomain() =
        Movement(
            type = MovementType.valueOf(type),
            cardId = cardId,
            day = SimulationDay(day),
            reason = reason,
        )
}
