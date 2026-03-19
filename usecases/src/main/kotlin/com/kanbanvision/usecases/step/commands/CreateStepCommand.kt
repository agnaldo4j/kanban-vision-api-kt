package com.kanbanvision.usecases.step.commands

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.usecases.cqs.Command

data class CreateStepCommand(
    val boardId: String,
    val name: String,
    val requiredAbility: AbilityName,
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(boardId.isNotBlank()) { DomainError.ValidationError("Board id must not be blank") }
            ensure(name.isNotBlank()) { DomainError.ValidationError("Step name must not be blank") }
        }
}
