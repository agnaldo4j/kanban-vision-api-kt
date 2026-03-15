package com.kanbanvision.usecases.board

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.usecases.board.queries.GetBoardQuery
import com.kanbanvision.usecases.repositories.BoardRepository
import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

class GetBoardUseCase(
    private val boardRepository: BoardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetBoardQuery): Either<DomainError, Board> =
        either {
            query.validate().bind()
            log.debug("Fetching board: id={}", query.id)
            val (result, duration) = measureTimedValue { boardRepository.findById(BoardId(query.id)) }
            val board = result.bind()
            log.info("Board fetched: id={} duration={}ms", query.id, duration.inWholeMilliseconds)
            board
        }
}
