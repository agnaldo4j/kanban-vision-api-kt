package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.PolicySet
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.SimulationContext
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.domain.model.Squad
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.Tribe
import com.kanbanvision.domain.model.Worker
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

@Serializable
internal data class WorkItemSurrogate(
    val id: String,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("stepId", "columnId")
    val stepId: String = "",
    val title: String,
    val description: String = "",
    val position: Int = 0,
    val serviceClass: String,
    val state: String,
    val agingDays: Int,
    val analysisEffort: Int = 0,
    val developmentEffort: Int = 0,
    val testEffort: Int = 0,
    val deployEffort: Int = 0,
    val remainingAnalysisEffort: Int = analysisEffort,
    val remainingDevelopmentEffort: Int = developmentEffort,
    val remainingTestEffort: Int = testEffort,
    val remainingDeployEffort: Int = deployEffort,
)

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
    val abilities: List<AbilitySurrogate> = emptyList(),
)

@Serializable
internal data class SquadSurrogate(
    val id: String,
    val name: String,
    val workers: List<WorkerSurrogate> = emptyList(),
)

@Serializable
internal data class TribeSurrogate(
    val id: String,
    val name: String,
    val squads: List<SquadSurrogate> = emptyList(),
)

@Serializable
internal data class SimulationContextSurrogate(
    val organizationId: String,
    val boardId: String,
    val steps: List<StepSurrogate> = emptyList(),
    val tribes: List<TribeSurrogate> = emptyList(),
    val workerAssignments: Map<String, String> = emptyMap(),
)

@Serializable
internal data class StepSurrogate(
    val id: String,
    val boardId: String,
    val name: String,
    val position: Int,
    val requiredAbility: String,
)

@Serializable
internal data class SimulationStateSurrogate(
    val currentDay: Int,
    val wipLimit: Int,
    val cards: List<WorkItemSurrogate> = emptyList(),
    val context: SimulationContextSurrogate? = null,
    // Backward compatibility for persisted JSON from previous versions.
    val items: List<WorkItemSurrogate> = emptyList(),
)

internal object SimulationStateSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: SimulationState): String = json.encodeToString(state.toSurrogate())

    fun decode(raw: String): SimulationState = json.decodeFromString<SimulationStateSurrogate>(raw).toDomain()
}

private fun SimulationState.toSurrogate() =
    SimulationStateSurrogate(
        currentDay = currentDay.value,
        wipLimit = policySet.wipLimit,
        cards = cards.map { it.toSurrogate() },
        context = context?.toSurrogate(),
    )

private fun Card.toSurrogate() =
    WorkItemSurrogate(
        id = id,
        stepId = stepId,
        title = title,
        description = description,
        position = position,
        serviceClass = serviceClass.name,
        state = state.name,
        agingDays = agingDays,
        analysisEffort = analysisEffort,
        developmentEffort = developmentEffort,
        testEffort = testEffort,
        deployEffort = deployEffort,
        remainingAnalysisEffort = remainingAnalysisEffort,
        remainingDevelopmentEffort = remainingDevelopmentEffort,
        remainingTestEffort = remainingTestEffort,
        remainingDeployEffort = remainingDeployEffort,
    )

private fun SimulationStateSurrogate.toDomain() =
    SimulationState(
        currentDay = SimulationDay(currentDay),
        policySet = PolicySet(wipLimit = wipLimit),
        cards = (cards.takeIf { it.isNotEmpty() } ?: items).map { it.toDomain() },
        context = context?.toDomain(),
    )

private fun WorkItemSurrogate.toDomain() =
    Card(
        id = id,
        stepId = stepId,
        title = title,
        description = description,
        position = position,
        serviceClass = ServiceClass.valueOf(serviceClass),
        state = CardState.valueOf(state),
        agingDays = agingDays,
        analysisEffort = analysisEffort,
        developmentEffort = developmentEffort,
        testEffort = testEffort,
        deployEffort = deployEffort,
        remainingAnalysisEffort = remainingAnalysisEffort,
        remainingDevelopmentEffort = remainingDevelopmentEffort,
        remainingTestEffort = remainingTestEffort,
        remainingDeployEffort = remainingDeployEffort,
    )

private fun SimulationContext.toSurrogate() =
    SimulationContextSurrogate(
        organizationId = organizationId,
        boardId = boardId,
        steps = steps.map { it.toSurrogate() },
        tribes = tribes.map { it.toSurrogate() },
        workerAssignments = workerAssignments,
    )

private fun Tribe.toSurrogate() =
    TribeSurrogate(
        id = id,
        name = name,
        squads = squads.map { it.toSurrogate() },
    )

private fun Squad.toSurrogate() =
    SquadSurrogate(
        id = id,
        name = name,
        workers = workers.map { it.toSurrogate() },
    )

private fun Worker.toSurrogate() =
    WorkerSurrogate(
        id = id,
        name = name,
        abilities =
            abilities.map {
                AbilitySurrogate(
                    id = it.id,
                    name = it.name.name,
                    seniority = it.seniority.name,
                )
            },
    )

private fun SimulationContextSurrogate.toDomain() =
    SimulationContext(
        organizationId = organizationId,
        boardId = boardId,
        steps = steps.map { it.toDomain() },
        tribes = tribes.map { it.toDomain() },
        workerAssignments = workerAssignments,
    )

private fun Step.toSurrogate() =
    StepSurrogate(
        id = id,
        boardId = boardId,
        name = name,
        position = position,
        requiredAbility = requiredAbility.name,
    )

private fun StepSurrogate.toDomain() =
    Step(
        id = id,
        boardId = boardId,
        name = name,
        position = position,
        requiredAbility = AbilityName.valueOf(requiredAbility),
    )

private fun TribeSurrogate.toDomain() =
    Tribe(
        id = id,
        name = name,
        squads = squads.map { it.toDomain() },
    )

private fun SquadSurrogate.toDomain() =
    Squad(
        id = id,
        name = name,
        workers = workers.map { it.toDomain() },
    )

private fun WorkerSurrogate.toDomain() =
    Worker(
        id = id,
        name = name,
        abilities =
            abilities
                .map {
                    Ability(
                        id = it.id,
                        name = AbilityName.valueOf(it.name),
                        seniority = Seniority.valueOf(it.seniority),
                    )
                }.toSet(),
    )
