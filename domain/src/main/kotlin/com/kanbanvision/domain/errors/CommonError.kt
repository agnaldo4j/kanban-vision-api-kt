package com.kanbanvision.domain.errors

/**
 * Erros genéricos, transversais aos bounded contexts (futuro `domain-common` — ADR-0038).
 */
sealed interface CommonError : DomainError {
    data class ValidationError(
        val messages: List<String>,
    ) : CommonError {
        init {
            require(messages.isNotEmpty()) { "ValidationError must have at least one message" }
        }

        constructor(message: String) : this(listOf(message))

        val message: String get() = messages.joinToString("; ")
    }

    data class PersistenceError(
        val message: String,
    ) : CommonError

    data class ServiceUnavailable(
        val service: String,
        val reason: String,
    ) : CommonError

    data class Forbidden(
        val reason: String,
    ) : CommonError
}
