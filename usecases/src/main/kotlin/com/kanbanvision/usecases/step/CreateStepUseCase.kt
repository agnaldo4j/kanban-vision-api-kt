package com.kanbanvision.usecases.step

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.CreateColumnUseCase
import com.kanbanvision.usecases.column.commands.CreateColumnCommand
import com.kanbanvision.usecases.step.commands.CreateStepCommand

class CreateStepUseCase(
    private val createColumnUseCase: CreateColumnUseCase,
) {
    suspend fun execute(command: CreateStepCommand): Either<DomainError, ColumnId> =
        createColumnUseCase.execute(
            CreateColumnCommand(
                boardId = command.boardId,
                name = command.name,
                requiredAbility = command.requiredAbility,
            ),
        )
}
