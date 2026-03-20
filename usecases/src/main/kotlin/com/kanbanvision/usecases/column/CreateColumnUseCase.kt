package com.kanbanvision.usecases.column

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.column.commands.CreateColumnCommand
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.ColumnRepository
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class CreateColumnUseCase(
    private val columnRepository: ColumnRepository,
    private val boardRepository: BoardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateColumnCommand): Either<DomainError, String> =
        either {
            command.validate().bind()
            logCreateStart(command)
            val (columnId, duration) = timed { createColumn(command) }
            logCreateSuccess(command, columnId, duration.inWholeMilliseconds)
            columnId
        }

    private suspend fun createColumn(command: CreateColumnCommand): Either<DomainError, String> =
        either {
            val boardId = command.boardId
            val board = boardRepository.findById(boardId).bind()
            val existingColumns = columnRepository.findByBoardId(boardId).bind()
            val hydratedBoard = board.copy(columns = existingColumns)
            val column = createDomainColumn(command, hydratedBoard)
            columnRepository.save(column).bind()
            column.id
        }

    private fun Raise<DomainError>.createDomainColumn(
        command: CreateColumnCommand,
        hydratedBoard: com.kanbanvision.domain.model.Board,
    ): Step =
        try {
            hydratedBoard.addColumn(command.name, command.requiredAbility)
        } catch (e: IllegalArgumentException) {
            raise(DomainError.ValidationError(e.message ?: "Invalid column"))
        }

    private fun logCreateStart(command: CreateColumnCommand) {
        log.debug(
            "Creating column: boardId={} name={} requiredAbility={}",
            command.boardId,
            command.name,
            command.requiredAbility,
        )
    }

    private fun logCreateSuccess(
        command: CreateColumnCommand,
        columnId: String,
        durationMs: Long,
    ) {
        log.info(
            "Step created: id={} boardId={} name={} duration={}ms",
            columnId,
            command.boardId,
            command.name,
            durationMs,
        )
    }
}
