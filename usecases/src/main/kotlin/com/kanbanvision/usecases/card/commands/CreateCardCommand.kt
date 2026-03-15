package com.kanbanvision.usecases.card.commands

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Command

data class CreateCardCommand(
    val columnId: String,
    val title: String,
    val description: String = "",
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(columnId.isNotBlank()) { DomainError.ValidationError("Column id must not be blank") }
            ensure(title.isNotBlank()) { DomainError.ValidationError("Card title must not be blank") }
        }
}
