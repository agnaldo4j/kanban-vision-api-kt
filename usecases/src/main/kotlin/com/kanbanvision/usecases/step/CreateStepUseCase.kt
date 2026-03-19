package com.kanbanvision.usecases.step

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.CreateColumnUseCase
import com.kanbanvision.usecases.column.commands.CreateColumnCommand
import com.kanbanvision.usecases.step.commands.CreateStepCommand

class CreateStepUseCase(
    private val createColumnUseCase: CreateColumnUseCase,
) {
    suspend fun execute(command: CreateStepCommand): Either<DomainError, ColumnId> =
        either {
            command.validate().bind()
            val result =
                createColumnUseCase.execute(
                    CreateColumnCommand(
                        boardId = command.boardId,
                        name = command.name,
                        requiredAbility = command.requiredAbility,
                    ),
                )
            result.bind()
        }
}
