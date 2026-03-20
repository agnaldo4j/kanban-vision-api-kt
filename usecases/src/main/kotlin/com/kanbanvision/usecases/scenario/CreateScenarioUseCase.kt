package com.kanbanvision.usecases.scenario

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioConfig
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.usecases.repositories.OrganizationRepository
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.scenario.commands.CreateScenarioCommand
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class CreateScenarioUseCase(
    private val organizationRepository: OrganizationRepository,
    private val scenarioRepository: ScenarioRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateScenarioCommand): Either<DomainError, String> =
        either {
            command.validate().bind()
            val organizationId = command.organizationId
            organizationRepository.findById(organizationId).bind()
            val scenario = buildScenario(organizationId, command)
            val initialState = SimulationState.initial(scenario.config)
            val (id, duration) = timed { persist(scenario, initialState) }
            log.info("Scenario created: id={} duration={}ms", id, duration.inWholeMilliseconds)
            id
        }

    private fun buildScenario(
        organizationId: String,
        command: CreateScenarioCommand,
    ): Scenario =
        Scenario.create(
            organizationId = organizationId,
            config =
                ScenarioConfig(
                    wipLimit = command.wipLimit,
                    teamSize = command.teamSize,
                    seedValue = command.seedValue,
                ),
        )

    private suspend fun persist(
        scenario: Scenario,
        state: SimulationState,
    ): Either<DomainError, String> =
        either {
            scenarioRepository.save(scenario).bind()
            scenarioRepository.saveState(scenario.id, state).bind()
            scenario.id
        }
}
