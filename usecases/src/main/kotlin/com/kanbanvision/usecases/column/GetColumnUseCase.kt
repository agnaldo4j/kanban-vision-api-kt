package com.kanbanvision.usecases.column

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.queries.GetColumnQuery
import com.kanbanvision.usecases.repositories.ColumnRepository
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetColumnUseCase(
    private val columnRepository: ColumnRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetColumnQuery): Either<DomainError, Column> =
        either {
            query.validate().bind()
            log.debug("Fetching column: id={}", query.id)
            val (column, duration) = timed { columnRepository.findById(ColumnId(query.id)) }
            log.info("Column fetched: id={} duration={}ms", query.id, duration.inWholeMilliseconds)
            column
        }
}
