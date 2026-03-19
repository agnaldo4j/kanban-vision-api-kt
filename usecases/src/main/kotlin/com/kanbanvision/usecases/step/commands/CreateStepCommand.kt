package com.kanbanvision.usecases.step.commands

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.usecases.column.commands.CreateColumnCommand
import com.kanbanvision.usecases.cqs.Command

data class CreateStepCommand(
    val boardId: String,
    val name: String,
    val requiredAbility: AbilityName,
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        CreateColumnCommand(
            boardId = boardId,
            name = name,
            requiredAbility = requiredAbility,
        ).validate()
}
