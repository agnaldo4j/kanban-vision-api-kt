package com.kanbanvision.usecases.scenario

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.scenario.queries.GetScenarioQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

data class ScenarioWithState(
    val scenario: Scenario,
    val state: SimulationState,
)

class GetScenarioUseCase(
    private val scenarioRepository: ScenarioRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetScenarioQuery): Either<DomainError, ScenarioWithState> =
        either {
            query.validate().bind()
            val id = query.scenarioId
            val (result, duration) = timed { load(id) }
            log.info("Scenario fetched: id={} duration={}ms", id, duration.inWholeMilliseconds)
            result
        }

    private suspend fun load(id: String): Either<DomainError, ScenarioWithState> =
        either {
            val scenario = scenarioRepository.findById(id).bind()
            val state = scenarioRepository.findState(id).bind()
            ScenarioWithState(scenario = scenario, state = state)
        }
}
