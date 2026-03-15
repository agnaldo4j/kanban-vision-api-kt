package com.kanbanvision.usecases.card.commands

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Command

data class MoveCardCommand(
    val cardId: String,
    val targetColumnId: String,
    val newPosition: Int,
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either<NonEmptyList<DomainError.ValidationError>, Unit> {
            zipOrAccumulate(
                { ensure(cardId.isNotBlank()) { DomainError.ValidationError("Card id must not be blank") } },
                { ensure(targetColumnId.isNotBlank()) { DomainError.ValidationError("Target column id must not be blank") } },
                { ensure(newPosition >= 0) { DomainError.ValidationError("Position must be non-negative") } },
            ) { _, _, _ -> }
        }.mapLeft { errors -> DomainError.ValidationError(errors.joinToString("; ") { it.message }) }
}
