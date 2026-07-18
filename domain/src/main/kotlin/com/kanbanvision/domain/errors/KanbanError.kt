package com.kanbanvision.domain.errors

/**
 * Erros do Kanban Management BC (futuro `domain-kanban` — ADR-0038).
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
}
