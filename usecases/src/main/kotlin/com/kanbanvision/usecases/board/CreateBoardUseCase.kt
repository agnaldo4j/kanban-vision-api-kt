package com.kanbanvision.usecases.board

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.usecases.board.commands.CreateBoardCommand
import com.kanbanvision.usecases.repositories.BoardRepository
import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

class CreateBoardUseCase(
    private val boardRepository: BoardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateBoardCommand): BoardId {
        command.validate()
        log.debug("Creating board: name={}", command.name)
        val (boardId, duration) =
            measureTimedValue {
                val board = Board.create(command.name)
                boardRepository.save(board)
                board.id
            }
        log.info("Board created: id={} name={} duration={}ms", boardId.value, command.name, duration.inWholeMilliseconds)
        return boardId
    }
}
