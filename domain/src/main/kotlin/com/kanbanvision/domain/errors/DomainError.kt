package com.kanbanvision.domain.errors

sealed class DomainError {
    data class ValidationError(
        val messages: List<String>,
    ) : DomainError() {
        init {
            require(messages.isNotEmpty()) { "ValidationError must have at least one message" }
        }

        constructor(message: String) : this(listOf(message))

        val message: String get() = messages.joinToString("; ")
    }

    data class BoardNotFound(
        val id: String,
    ) : DomainError()

    data class CardNotFound(
        val id: String,
    ) : DomainError()

    data class ColumnNotFound(
        val id: String,
    ) : DomainError()

    data class PersistenceError(
        val message: String,
    ) : DomainError()
}
