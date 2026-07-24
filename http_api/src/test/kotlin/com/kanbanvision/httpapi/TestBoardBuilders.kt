package com.kanbanvision.httpapi

import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.StepId

/**
 * Helpers de teste (ADR-0044): `Board.addStep`/`addCard` retornam `Either<KanbanError, Board>`.
 * Estes wrappers desembrulham o `Right` para montar um Board VÁLIDO em setup de teste.
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
