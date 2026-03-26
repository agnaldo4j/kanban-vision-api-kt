package com.kanbanvision.persistence

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.Logger

internal suspend fun <T> dbQuery(
    log: Logger,
    block: () -> T,
): Either<DomainError, T> =
    withContext(Dispatchers.IO) {
        Either
            .catch {
                transaction { block() }
            }.mapLeft { e ->
                if (e is CancellationException) throw e
                log.error("Persistence error", e)
                DomainError.PersistenceError(e.message ?: "Database error")
            }
    }
