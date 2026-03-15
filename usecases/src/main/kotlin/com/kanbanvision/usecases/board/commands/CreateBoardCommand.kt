package com.kanbanvision.usecases.board.commands

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Command

data class CreateBoardCommand(
    val name: String,
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(name.isNotBlank()) { DomainError.ValidationError("Board name must not be blank") }
        }
}
