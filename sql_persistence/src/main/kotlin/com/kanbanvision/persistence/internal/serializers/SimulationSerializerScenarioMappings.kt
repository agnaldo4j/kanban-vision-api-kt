package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.common.model.NonBlankName
import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.BoardId
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
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
        id = ScenarioId(id),
        name = NonBlankName(name),
        rules = rules.toDomain(),
        board = board.toDomain(),
    )

private fun ScenarioRules.toSurrogate() =
    ScenarioRulesSurrogate(
        id = id,
        policySet = policySet.toSurrogate(),
        // Legacy wire field: kept so a pod running an older release can still decode blobs written
        // by this one during a rolling deploy. Mirrors policySet.wipLimit via the delegating accessor.
        wipLimit = wipLimit,
        teamSize = teamSize,
        seedValue = seedValue,
    )

private fun ScenarioRulesSurrogate.toDomain() =
    ScenarioRules(
        id = id,
        // policySet is the single source of the WIP limit; the surrogate's own wipLimit is a legacy
        // wire field and is deliberately ignored, even when a legacy blob has it diverging.
        policySet = policySet.toDomain(),
        teamSize = teamSize,
        seedValue = seedValue,
    )

private fun PolicySet.toSurrogate() = PolicySetSurrogate(id = id, wipLimit = wipLimit)

private fun PolicySetSurrogate.toDomain() = PolicySet(id = id, wipLimit = wipLimit)

private fun Board.toSurrogate() = BoardSurrogate(id = id.value, name = name.value, steps = steps.map { it.toSurrogate() })

private fun BoardSurrogate.toDomain() = Board(id = BoardId(id), name = NonBlankName(name), steps = steps.map { it.toDomain() })

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

private fun StepSurrogate.toDomain() =
    Step(
        id = StepId(id),
        board = BoardId(boardId),
        name = NonBlankName(name),
        position = position,
        requiredAbility = AbilityName.valueOf(requiredAbility),
        cards = cards.map { it.toDomain() },
        workers = workers.map { it.toDomain() },
    )

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
        id = CardId(id),
        step = StepId(stepId),
        title = NonBlankTitle(title),
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
