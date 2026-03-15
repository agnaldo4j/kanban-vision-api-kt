package com.kanbanvision.usecases.board

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.usecases.board.queries.GetBoardQuery
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetBoardUseCase(
    private val boardRepository: BoardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetBoardQuery): Either<DomainError, Board> =
        either {
            query.validate().bind()
            log.debug("Fetching board: id={}", query.id)
            val (board, duration) = timed { boardRepository.findById(BoardId(query.id)) }
            log.info("Board fetched: id={} duration={}ms", query.id, duration.inWholeMilliseconds)
            board
        }
}
