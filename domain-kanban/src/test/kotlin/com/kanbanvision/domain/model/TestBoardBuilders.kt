package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.StepId
import com.kanbanvision.domain.model.kanban.Worker
import java.time.Instant

/**
 * Helpers de teste (ADR-0044): as operações de agregado retornam `Either<KanbanError, _>`. Estes wrappers
 * desembrulham o `Right` para montar estado VÁLIDO em setup de teste — use só com dados válidos.
 */
internal fun Board.withStep(
    name: String,
    requiredAbility: AbilityName,
): Board = addStep(name, requiredAbility).getOrNull() ?: error("test fixture: addStep('$name') falhou")

internal fun Board.withCard(
    step: StepId,
    title: String,
    description: String = "",
): Board = addCard(step, title, description).getOrNull() ?: error("test fixture: addCard('$title') falhou")

internal fun Step.withWorker(worker: Worker): Step = assignWorker(worker).getOrNull() ?: error("test fixture: assignWorker falhou")

internal fun Step.execute(
    worker: Worker,
    card: Card,
    dailyCapacities: Map<AbilityName, Int>,
    now: Instant,
): Step.ExecutionResult = executeCard(worker, card, dailyCapacities, now).getOrNull() ?: error("test fixture: executeCard falhou")

internal fun Card.blockOrThrow(): Card = block().getOrNull() ?: error("test fixture: block falhou")
