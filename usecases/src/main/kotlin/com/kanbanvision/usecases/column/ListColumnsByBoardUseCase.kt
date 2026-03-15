package com.kanbanvision.usecases.column

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.usecases.column.queries.ListColumnsByBoardQuery
import com.kanbanvision.usecases.repositories.ColumnRepository
import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

class ListColumnsByBoardUseCase(
    private val columnRepository: ColumnRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: ListColumnsByBoardQuery): Either<DomainError, List<Column>> =
        either {
            query.validate().bind()
            log.debug("Listing columns: boardId={}", query.boardId)
            val (result, duration) = measureTimedValue { columnRepository.findByBoardId(BoardId(query.boardId)) }
            val columns = result.bind()
            log.info(
                "Columns listed: boardId={} count={} duration={}ms",
                query.boardId,
                columns.size,
                duration.inWholeMilliseconds,
            )
            columns
        }
}
