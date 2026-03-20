package com.kanbanvision.usecases.step

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.step.commands.CreateStepCommand
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class CreateStepUseCase(
    private val stepRepository: StepRepository,
    private val boardRepository: BoardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateStepCommand): Either<DomainError, ColumnId> =
        either {
            command.validate().bind()
            log.debug(
                "Creating step: boardId={} name={} requiredAbility={}",
                command.boardId,
                command.name,
                command.requiredAbility,
            )
            val (stepId, duration) = timed { createStep(command) }
            log.info(
                "Step created: id={} boardId={} name={} duration={}ms",
                stepId.value,
                command.boardId,
                command.name,
                duration.inWholeMilliseconds,
            )
            stepId
        }

    private suspend fun createStep(command: CreateStepCommand): Either<DomainError, ColumnId> =
        either {
            val boardId = BoardId(command.boardId)
            boardRepository.findById(boardId).bind()
            val existingSteps = stepRepository.findByBoardId(boardId).bind()
            val createdStep = createDomainStep(command, boardId, existingSteps)
            stepRepository.save(createdStep).bind()
            createdStep.id
        }

    private fun Raise<DomainError>.createDomainStep(
        command: CreateStepCommand,
        boardId: BoardId,
        existingSteps: List<Step>,
    ): Step =
        try {
            require(existingSteps.none { it.name == command.name }) {
                "Step name '${command.name}' already exists on this board"
            }
            Step.create(
                boardId = boardId,
                name = command.name,
                position = existingSteps.size,
                requiredAbility = command.requiredAbility,
            )
        } catch (e: IllegalArgumentException) {
            raise(DomainError.ValidationError(e.message ?: "Invalid step"))
        }
}
