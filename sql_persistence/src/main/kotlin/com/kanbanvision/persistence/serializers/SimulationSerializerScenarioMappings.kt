package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.BoardRef
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.PolicySet
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.StepRef

internal fun Scenario.toSurrogate() =
    ScenarioSurrogate(
        id = id,
        name = name,
        rules = rules.toSurrogate(),
        board = board.toSurrogate(),
        decisions = decisions.map { it.toSurrogate() },
        history = history.map { it.toSurrogate() },
    )

internal fun ScenarioSurrogate.toDomain() =
    Scenario(
        id = id,
        name = name,
        rules = rules.toDomain(),
        board = board.toDomain(),
        decisions = decisions.map { it.toDomain() },
        history = history.map { it.toDomain() },
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
        id = id,
        policySet = policySet.toDomain(),
        wipLimit = wipLimit,
        teamSize = teamSize,
        seedValue = seedValue,
    )

private fun PolicySet.toSurrogate() = PolicySetSurrogate(id = id, wipLimit = wipLimit)

private fun PolicySetSurrogate.toDomain() = PolicySet(id = id, wipLimit = wipLimit)

private fun Board.toSurrogate() = BoardSurrogate(id = id, name = name, steps = steps.map { it.toSurrogate() })

private fun BoardSurrogate.toDomain() = Board(id = id, name = name, steps = steps.map { it.toDomain() })

private fun Step.toSurrogate() =
    StepSurrogate(
        id = id,
        boardId = board.id,
        name = name,
        position = position,
        requiredAbility = requiredAbility.name,
        cards = cards.map { it.toSurrogate() },
        workers = workers.map { it.toSurrogate() },
    )

private fun StepSurrogate.toDomain() =
    Step(
        id = id,
        board = BoardRef(boardId),
        name = name,
        position = position,
        requiredAbility = AbilityName.valueOf(requiredAbility),
        cards = cards.map { it.toDomain() },
        workers = workers.map { it.toDomain() },
    )

private fun Card.toSurrogate() =
    CardSurrogate(
        id = id,
        stepId = step.id,
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

private fun CardSurrogate.toDomain() =
    Card(
        id = id,
        step = StepRef(stepId),
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
