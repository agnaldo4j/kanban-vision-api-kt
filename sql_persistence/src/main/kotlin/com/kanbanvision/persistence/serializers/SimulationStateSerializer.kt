package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.Simulation
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class AbilitySurrogate(
    val id: String,
    val name: String,
    val seniority: String,
)

@Serializable
internal data class WorkerSurrogate(
    val id: String,
    val name: String,
    val abilities: List<AbilitySurrogate>,
)

@Serializable
internal data class SquadSurrogate(
    val id: String,
    val name: String,
    val workers: List<WorkerSurrogate>,
)

@Serializable
internal data class TribeSurrogate(
    val id: String,
    val name: String,
    val squads: List<SquadSurrogate>,
)

@Serializable
internal data class OrganizationSurrogate(
    val id: String,
    val name: String,
    val tribes: List<TribeSurrogate>,
)

@Serializable
internal data class CardSurrogate(
    val id: String,
    val stepId: String,
    val title: String,
    val description: String,
    val position: Int,
    val serviceClass: String,
    val state: String,
    val agingDays: Int,
    val analysisEffort: Int,
    val developmentEffort: Int,
    val testEffort: Int,
    val deployEffort: Int,
    val remainingAnalysisEffort: Int,
    val remainingDevelopmentEffort: Int,
    val remainingTestEffort: Int,
    val remainingDeployEffort: Int,
)

@Serializable
internal data class StepSurrogate(
    val id: String,
    val boardId: String,
    val name: String,
    val position: Int,
    val requiredAbility: String,
    val cards: List<CardSurrogate>,
    val workers: List<WorkerSurrogate>,
)

@Serializable
internal data class BoardSurrogate(
    val id: String,
    val name: String,
    val steps: List<StepSurrogate>,
)

@Serializable
internal data class PolicySetSurrogate(
    val id: String,
    val wipLimit: Int,
)

@Serializable
internal data class ScenarioRulesSurrogate(
    val id: String,
    val policySet: PolicySetSurrogate,
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long,
)

@Serializable
internal data class DecisionSurrogate(
    val id: String,
    val type: String,
    val payload: Map<String, String>,
)

@Serializable
internal data class FlowMetricsSurrogate(
    val id: String,
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
)

@Serializable
internal data class MovementSurrogate(
    val id: String,
    val type: String,
    val cardId: String,
    val day: Int,
    val reason: String,
)

@Serializable
internal data class DailySnapshotSurrogate(
    val id: String,
    val simulationId: String,
    val scenarioId: String,
    val day: Int,
    val metrics: FlowMetricsSurrogate,
    val movements: List<MovementSurrogate>,
)

@Serializable
internal data class ScenarioSurrogate(
    val id: String,
    val name: String,
    val rules: ScenarioRulesSurrogate,
    val board: BoardSurrogate,
    val decisions: List<DecisionSurrogate>,
    val history: List<DailySnapshotSurrogate>,
)

@Serializable
internal data class SimulationSurrogate(
    val id: String,
    val name: String,
    val currentDay: Int,
    val status: String,
    val organization: OrganizationSurrogate,
    val scenario: ScenarioSurrogate,
)

internal object SimulationSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(simulation: Simulation): String = json.encodeToString(simulation.toSurrogate())

    fun decode(raw: String): Simulation = json.decodeFromString<SimulationSurrogate>(raw).toDomain()
}
