package com.kanbanvision.usecases.column

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.column.queries.ListColumnsByBoardQuery
import com.kanbanvision.usecases.repositories.ColumnRepository
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class ListColumnsByBoardUseCase(
    private val columnRepository: ColumnRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: ListColumnsByBoardQuery): Either<DomainError, List<Step>> =
        either {
            query.validate().bind()
            log.debug("Listing columns: boardId={}", query.boardId)
            val (columns, duration) = timed { columnRepository.findByBoardId(query.boardId) }
            log.info(
                "Columns listed: boardId={} count={} duration={}ms",
                query.boardId,
                columns.size,
                duration.inWholeMilliseconds,
            )
            columns
        }
}
