package com.kanbanvision.usecases.scenario.queries

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Query

data class GetScenarioQuery(
    val scenarioId: String,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(scenarioId.isNotBlank()) { DomainError.ValidationError("Scenario id must not be blank") }
        }
}
