package com.kanbanvision.usecases.scenario

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioConfig
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.repositories.TenantRepository
import com.kanbanvision.usecases.scenario.commands.CreateScenarioCommand
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class CreateScenarioUseCase(
    private val tenantRepository: TenantRepository,
    private val scenarioRepository: ScenarioRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateScenarioCommand): Either<DomainError, String> =
        either {
            command.validate().bind()
            val tenantId = command.tenantId
            tenantRepository.findById(tenantId).bind()
            val scenario = buildScenario(tenantId, command)
            val initialState = SimulationState.initial(scenario.config)
            val (id, duration) = timed { persist(scenario, initialState) }
            log.info("Scenario created: id={} duration={}ms", id, duration.inWholeMilliseconds)
            id
        }

    private fun buildScenario(
        tenantId: String,
        command: CreateScenarioCommand,
    ): Scenario =
        Scenario.create(
            tenantId = tenantId,
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
