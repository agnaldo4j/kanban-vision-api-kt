package com.kanbanvision.usecases.board

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

    suspend fun execute(query: GetBoardQuery): Board {
        query.validate()
        log.debug("Fetching board: id={}", query.id)
        val (board, duration) =
            measureTimedValue {
                boardRepository.findById(BoardId(query.id))
                    ?: throw NoSuchElementException("Board '${query.id}' not found")
            }
        log.info("Board fetched: id={} duration={}ms", query.id, duration.inWholeMilliseconds)
        return board
    }
}
