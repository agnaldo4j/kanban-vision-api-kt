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
        // Legacy wire field: kept so a pod running an older release can still decode blobs written
        // by this one during a rolling deploy. Mirrors policySet.wipLimit via the delegating accessor.
        wipLimit = wipLimit,
        teamSize = teamSize,
        seedValue = seedValue,
    )

private fun ScenarioRulesSurrogate.toDomain() =
    ScenarioRules(
        id = decodeId(id),
        // policySet is the single source of the WIP limit; the surrogate's own wipLimit is a legacy
        // wire field and is deliberately ignored, even when a legacy blob has it diverging.
        policySet = policySet.toDomain(),
        // ScenarioRules.init exige teamSize > 0 (GAP-DV): coage em vez de deixar lançar.
        teamSize = teamSize.coerceAtLeast(1),
        seedValue = seedValue,
    )

private fun PolicySet.toSurrogate() = PolicySetSurrogate(id = id, wipLimit = wipLimit)

// PolicySet.init exige wipLimit > 0 (GAP-DV).
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

private fun StepSurrogate.toDomain() =
    Step(
        id = StepId(decodeId(id)),
        board = BoardId(decodeId(boardId)),
        name = decodeName(name),
        position = position.coerceAtLeast(0),
        requiredAbility = decodeEnum(requiredAbility, ABILITY_FALLBACK),
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
        id = decodeCardId(id),
        step = StepId(decodeId(stepId)),
        title = decodeTitle(title),
        description = description,
        position = position.coerceAtLeast(0),
        serviceClass = decodeEnum(serviceClass, ServiceClass.STANDARD),
        state = decodeEnum(state, CARD_STATE_FALLBACK),
        agingDays = agingDays.coerceAtLeast(0),
        // Card.init exige cada effort >= 0 e cada remaining em 0..effort (GAP-DV): clamp em vez de
        // lançar. O `analysisEffort.coerceAtLeast(0)` repetido no teto é intencional — o teto tem de
        // ser o valor JÁ coagido, senão um effort negativo daria um range invertido em coerceIn.
        analysisEffort = analysisEffort.coerceAtLeast(0),
        developmentEffort = developmentEffort.coerceAtLeast(0),
        testEffort = testEffort.coerceAtLeast(0),
        deployEffort = deployEffort.coerceAtLeast(0),
        remainingAnalysisEffort = remainingAnalysisEffort.coerceIn(0, analysisEffort.coerceAtLeast(0)),
        remainingDevelopmentEffort = remainingDevelopmentEffort.coerceIn(0, developmentEffort.coerceAtLeast(0)),
        remainingTestEffort = remainingTestEffort.coerceIn(0, testEffort.coerceAtLeast(0)),
        remainingDeployEffort = remainingDeployEffort.coerceIn(0, deployEffort.coerceAtLeast(0)),
    )
