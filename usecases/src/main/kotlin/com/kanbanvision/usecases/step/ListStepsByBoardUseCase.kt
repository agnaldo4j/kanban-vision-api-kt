package com.kanbanvision.usecases.step

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.board.queries.GetBoardQuery
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.step.queries.ListStepsByBoardQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class ListStepsByBoardUseCase(
    private val getBoardUseCase: GetBoardUseCase,
    private val stepRepository: StepRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: ListStepsByBoardQuery): Either<DomainError, List<Step>> =
        either {
            query.validate().bind()
            getBoardUseCase.execute(GetBoardQuery(query.boardId)).bind()
            log.debug("Listing steps: boardId={}", query.boardId)
            val (steps, duration) =
                timed {
                    stepRepository
                        .findByBoardId(query.boardId)
                }
            log.info(
                "Steps listed: boardId={} count={} duration={}ms",
                query.boardId,
                steps.size,
                duration.inWholeMilliseconds,
            )
            steps
        }
}
