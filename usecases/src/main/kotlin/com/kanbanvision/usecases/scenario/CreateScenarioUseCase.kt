package com.kanbanvision.usecases.scenario

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.ScenarioConfig
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.valueobjects.TenantId
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

    suspend fun execute(command: CreateScenarioCommand): Either<DomainError, ScenarioId> =
        either {
            command.validate().bind()
            val tenantId = TenantId(command.tenantId)
            tenantRepository.findById(tenantId).bind()
            val scenario = buildScenario(tenantId, command)
            val initialState = SimulationState.initial(scenario.config)
            val (id, duration) = timed { persist(scenario, initialState) }
            log.info("Scenario created: id={} duration={}ms", id.value, duration.inWholeMilliseconds)
            id
        }

    private fun buildScenario(
        tenantId: TenantId,
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
    ): Either<DomainError, ScenarioId> =
        either {
            scenarioRepository.save(scenario).bind()
            scenarioRepository.saveState(scenario.id, state).bind()
            scenario.id
        }
}
