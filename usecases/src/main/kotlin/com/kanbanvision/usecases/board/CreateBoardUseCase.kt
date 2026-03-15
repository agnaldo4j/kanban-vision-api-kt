package com.kanbanvision.usecases.board

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
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

    suspend fun execute(command: CreateBoardCommand): Either<DomainError, BoardId> =
        either {
            command.validate().bind()
            log.debug("Creating board: name={}", command.name)
            val board = Board.create(command.name)
            val (result, duration) = measureTimedValue { boardRepository.save(board) }
            result.bind()
            log.info("Board created: id={} name={} duration={}ms", board.id.value, command.name, duration.inWholeMilliseconds)
            board.id
        }
}
