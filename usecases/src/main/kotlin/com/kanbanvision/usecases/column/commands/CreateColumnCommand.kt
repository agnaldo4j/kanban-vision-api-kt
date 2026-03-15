package com.kanbanvision.usecases.column.commands

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Command

data class CreateColumnCommand(
    val boardId: String,
    val name: String,
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(boardId.isNotBlank()) { DomainError.ValidationError("Board id must not be blank") }
            ensure(name.isNotBlank()) { DomainError.ValidationError("Column name must not be blank") }
        }
}
