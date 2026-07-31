package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.BoardId
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.StepId
import com.kanbanvision.domain.model.organization.PolicySet
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioId
import com.kanbanvision.domain.model.simulation.ScenarioRules

internal fun Scenario.toSurrogate() =
    ScenarioSurrogate(
        id = id.value,
        name = name.value,
        rules = rules.toSurrogate(),
        board = board.toSurrogate(),
    )

internal fun ScenarioSurrogate.toDomain() =
    Scenario(
        id = ScenarioId(decodeId(id)),
        name = decodeName(name),
        rules = rules.toDomain(),
        board = board.toDomain(),
    )

private fun ScenarioRules.toSurrogate() =
    ScenarioRulesSurrogate(
        id = id,
        policySet = policySet.toSurrogate(),
        wipLimit = wipLimit,
        teamSize = teamSize,
        seedValue = seedValue,
    )

private fun ScenarioRulesSurrogate.toDomain() =
    ScenarioRules(
        id = decodeId(id),
        policySet = policySet.toDomain(),
        teamSize = teamSize.coerceAtLeast(1),
        seedValue = seedValue,
    )

private fun PolicySet.toSurrogate() = PolicySetSurrogate(id = id, wipLimit = wipLimit)

private fun PolicySetSurrogate.toDomain() = PolicySet(id = decodeId(id), wipLimit = wipLimit.coerceAtLeast(1))

private fun Board.toSurrogate() = BoardSurrogate(id = id.value, name = name.value, steps = steps.map { it.toSurrogate() })

private fun BoardSurrogate.toDomain() = Board(id = BoardId(decodeId(id)), name = decodeName(name), steps = steps.map { it.toDomain() })

private fun Step.toSurrogate() =
    StepSurrogate(
        id = id.value,
        boardId = board.value,
        name = name.value,
        position = position,
        requiredAbility = requiredAbility.name,
        cards = cards.map { it.toSurrogate() },
        workers = workers.map { it.toSurrogate() },
    )

private fun StepSurrogate.toDomain(): Step {
    val ability = decodeEnum(requiredAbility, CROSS_FIELD_NEUTRAL_ABILITY)
    return Step(
        id = StepId(decodeId(id)),
        board = BoardId(decodeId(boardId)),
        name = decodeName(name),
        position = position.coerceAtLeast(0),
        requiredAbility = ability,
        cards = cards.map { it.toDomain() },
        workers = workers.map { it.toDomain(ability) },
    )
}

private fun Card.toSurrogate() =
    CardSurrogate(
        id = id.value,
        stepId = step.value,
        title = title.value,
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

private fun CardSurrogate.toDomain() =
    Card(
        id = decodeCardId(id),
        step = StepId(decodeId(stepId)),
        title = decodeTitle(title),
        description = description,
        position = position.coerceAtLeast(0),
        serviceClass = ServiceClass.fromNameOrDefault(serviceClass),
        state = decodeEnum(state, QUARANTINE_CARD_STATE),
        agingDays = agingDays.coerceAtLeast(0),
        analysisEffort = analysisEffort.asEffort(),
        developmentEffort = developmentEffort.asEffort(),
        testEffort = testEffort.asEffort(),
        deployEffort = deployEffort.asEffort(),
        remainingAnalysisEffort = remainingAnalysisEffort.asRemainingEffortOf(analysisEffort),
        remainingDevelopmentEffort = remainingDevelopmentEffort.asRemainingEffortOf(developmentEffort),
        remainingTestEffort = remainingTestEffort.asRemainingEffortOf(testEffort),
        remainingDeployEffort = remainingDeployEffort.asRemainingEffortOf(deployEffort),
    )

private fun Int.asEffort(): Int = coerceAtLeast(0)

private fun Int.asRemainingEffortOf(effort: Int): Int = coerceIn(0, effort.asEffort())
