package com.kanbanvision.persistence.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.repositories.ColumnRepository
import com.kanbanvision.usecases.repositories.StepRepository

class JdbcStepRepository(
    private val delegate: ColumnRepository,
) : StepRepository {
    override suspend fun save(step: Step): Either<DomainError, Step> = delegate.save(step.toColumn()).map { it.toStep() }

    override suspend fun findById(id: ColumnId): Either<DomainError, Step> =
        delegate
            .findById(id)
            .map { it.toStep() }
            .mapLeft(::mapStepError)

    override suspend fun findByBoardId(boardId: BoardId): Either<DomainError, List<Step>> =
        delegate.findByBoardId(boardId).map { columns -> columns.map { it.toStep() } }

    private fun mapStepError(error: DomainError): DomainError =
        when (error) {
            is DomainError.ColumnNotFound -> DomainError.StepNotFound(error.id)
            else -> error
        }

    private fun Step.toColumn(): Column =
        Column(
            id = id,
            boardId = boardId,
            name = name,
            position = position,
            requiredAbility = requiredAbility,
            cards = cards,
        )

    private fun Column.toStep(): Step =
        Step(
            id = id,
            boardId = boardId,
            name = name,
            position = position,
            requiredAbility = requiredAbility,
            cards = cards,
        )
}
