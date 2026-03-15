package com.kanbanvision.domain.errors

sealed class DomainError {
    data class ValidationError(
        val message: String,
    ) : DomainError()

    data class BoardNotFound(
        val id: String,
    ) : DomainError()

    data class CardNotFound(
        val id: String,
    ) : DomainError()

    data class PersistenceError(
        val message: String,
    ) : DomainError()
}
