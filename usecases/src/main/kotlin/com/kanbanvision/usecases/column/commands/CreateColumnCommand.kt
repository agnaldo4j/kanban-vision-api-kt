package com.kanbanvision.usecases.column.commands

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.usecases.cqs.Command

data class CreateColumnCommand(
    val boardId: String,
    val name: String,
    val requiredAbility: AbilityName,
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either<NonEmptyList<DomainError.ValidationError>, Unit> {
            zipOrAccumulate(
                { ensure(boardId.isNotBlank()) { DomainError.ValidationError("Board id must not be blank") } },
                { ensure(name.isNotBlank()) { DomainError.ValidationError("Step name must not be blank") } },
            ) { _, _ -> }
        }.mapLeft { errors -> DomainError.ValidationError(errors.map { it.message }) }
}
