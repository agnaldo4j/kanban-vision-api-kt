package com.kanbanvision.usecases.column

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.column.queries.GetColumnQuery
import com.kanbanvision.usecases.repositories.ColumnRepository
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetColumnUseCase(
    private val columnRepository: ColumnRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetColumnQuery): Either<DomainError, Step> =
        either {
            query.validate().bind()
            log.debug("Fetching column: id={}", query.id)
            val (column, duration) = timed { columnRepository.findById(query.id) }
            log.info("Step fetched: id={} duration={}ms", query.id, duration.inWholeMilliseconds)
            column
        }
}
