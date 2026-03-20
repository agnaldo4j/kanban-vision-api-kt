package com.kanbanvision.usecases.card.commands

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Command

data class CreateCardCommand(
    val stepId: String,
    val title: String,
    val description: String = "",
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either<NonEmptyList<DomainError.ValidationError>, Unit> {
            zipOrAccumulate(
                { ensure(stepId.isNotBlank()) { DomainError.ValidationError("Step id must not be blank") } },
                { ensure(title.isNotBlank()) { DomainError.ValidationError("Card title must not be blank") } },
            ) { _, _ -> }
        }.mapLeft { errors -> DomainError.ValidationError(errors.map { it.message }) }
}
