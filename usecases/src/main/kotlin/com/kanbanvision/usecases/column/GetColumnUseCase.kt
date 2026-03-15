package com.kanbanvision.usecases.column

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.queries.GetColumnQuery
import com.kanbanvision.usecases.repositories.ColumnRepository
import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

class GetColumnUseCase(
    private val columnRepository: ColumnRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetColumnQuery): Either<DomainError, Column> =
        either {
            query.validate().bind()
            log.debug("Fetching column: id={}", query.id)
            val (maybeColumn, duration) =
                catch(
                    { measureTimedValue { columnRepository.findById(ColumnId(query.id)) } },
                ) { e -> raise(DomainError.PersistenceError(e.message ?: "Database error")) }
            val column = maybeColumn ?: raise(DomainError.ColumnNotFound(query.id))
            log.info("Column fetched: id={} duration={}ms", query.id, duration.inWholeMilliseconds)
            column
        }
}
