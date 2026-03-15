package com.kanbanvision.usecases.column

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.commands.CreateColumnCommand
import com.kanbanvision.usecases.repositories.ColumnRepository
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class CreateColumnUseCase(
    private val columnRepository: ColumnRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateColumnCommand): Either<DomainError, ColumnId> =
        either {
            command.validate().bind()
            log.debug("Creating column: boardId={} name={}", command.boardId, command.name)
            val (columnId, duration) =
                timed {
                    either {
                        val boardId = BoardId(command.boardId)
                        val existing = columnRepository.findByBoardId(boardId).bind()
                        val column =
                            Column.create(
                                boardId = boardId,
                                name = command.name,
                                position = existing.size,
                            )
                        columnRepository.save(column).bind()
                        column.id
                    }
                }
            log.info(
                "Column created: id={} boardId={} name={} duration={}ms",
                columnId.value,
                command.boardId,
                command.name,
                duration.inWholeMilliseconds,
            )
            columnId
        }
}
