package com.kanbanvision.usecases.step

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.step.queries.GetStepQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetStepUseCase(
    private val stepRepository: StepRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetStepQuery): Either<DomainError, Step> =
        either {
            query.validate().bind()
            log.debug("Fetching step: id={}", query.id)
            val (step, duration) =
                timed {
                    stepRepository
                        .findById(query.id)
                }
            log.info("Step fetched: id={} duration={}ms", query.id, duration.inWholeMilliseconds)
            step
        }
}
