package com.kanbanvision.domain.model.kanban

import com.kanbanvision.domain.common.errors.DomainError

/**
 * Erros do Kanban Management BC (`:domain-kanban` — GAP-CK/ADR-0038), co-localizados com o agregado.
 */
sealed interface KanbanError : DomainError {
    data class BoardNotFound(
        val id: String,
    ) : KanbanError

    data class CardNotFound(
        val id: String,
    ) : KanbanError

    data class StepNotFound(
        val id: String,
    ) : KanbanError

    data class OrganizationNotFound(
        val id: String,
    ) : KanbanError

    data class DuplicateStepName(
        val name: String,
    ) : KanbanError

    data class WorkerCannotExecuteStep(
        val workerId: String,
        val stepId: String,
    ) : KanbanError

    data class WorkerAlreadyAssigned(
        val workerId: String,
    ) : KanbanError

    data class CardNotInProgress(
        val cardId: String,
    ) : KanbanError
}
