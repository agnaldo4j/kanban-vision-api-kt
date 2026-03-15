package com.kanbanvision.usecases.board.commands

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Command

data class CreateBoardCommand(
    val name: String,
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        if (name.isNotBlank()) {
            Unit.right()
        } else {
            DomainError.ValidationError("Board name must not be blank").left()
        }
}
