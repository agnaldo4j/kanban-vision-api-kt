package com.kanbanvision.persistence

import arrow.core.Either
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.common.errors.DomainError
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.Logger

// Holder não-nulo: executeSupplier é API Java e o null-check de platform type do Kotlin
// rejeitaria blocos que legitimamente retornam null (ex.: findByDay sem snapshot).
private class DbResult<T>(
    val value: T,
)

internal suspend fun <T> dbQuery(
    log: Logger,
    block: () -> T,
): Either<DomainError, T> =
    withContext(Dispatchers.IO) {
        Either
            .catch {
                DbCircuitBreaker.circuitBreaker
                    .executeSupplier { DbResult(transaction { block() }) }
                    .value
            }.mapLeft { e -> toDomainError(log, e) }
    }

private fun toDomainError(
    log: Logger,
    e: Throwable,
): DomainError {
    if (e is CancellationException) throw e
    return if (e is CallNotPermittedException) {
        log.warn("Database circuit breaker open — call rejected")
        CommonError.ServiceUnavailable(service = "database", reason = "circuit breaker open")
    } else {
        log.error("Persistence error", e)
        CommonError.PersistenceError(e.message ?: "Database error")
    }
}
