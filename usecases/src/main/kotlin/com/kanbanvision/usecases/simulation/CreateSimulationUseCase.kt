package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.events.DomainEvent
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.organization.Scenario
import com.kanbanvision.domain.model.organization.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationStatus
import com.kanbanvision.usecases.ports.EventPublisherPort
import com.kanbanvision.usecases.repositories.OrganizationRepository
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.simulation.commands.CreateSimulationCommand
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class CreateSimulationUseCase(
    private val organizationRepository: OrganizationRepository,
    private val simulationRepository: SimulationRepository,
    private val publisher: EventPublisherPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val SIMULATION_NAME_ID_PREFIX_LENGTH = 8
    }

    suspend fun execute(command: CreateSimulationCommand): Either<DomainError, String> =
        either {
            command.validate().bind()
            val organization = organizationRepository.findById(command.organizationId).bind()

            val rules =
                ScenarioRules.create(
                    wipLimit = command.wipLimit,
                    teamSize = command.teamSize,
                    seedValue = command.seedValue,
                )
            val board = Board.create(name = "Main Board")
            val scenario = Scenario.create(name = "Default Simulation Scenario", rules = rules, board = board)
            val simulation =
                Simulation.create(
                    name = "Simulation ${scenario.id.value.take(SIMULATION_NAME_ID_PREFIX_LENGTH)}",
                    organization = organization,
                    scenario = scenario,
                    status = SimulationStatus.DRAFT,
                )

            val (id, duration) = timed { persist(simulation) }
            log.info("Simulation created: id={} duration={}ms", id, duration.inWholeMilliseconds)
            id
        }

    private suspend fun persist(simulation: Simulation): Either<DomainError, String> =
        either {
            simulationRepository.save(simulation).bind()
            publisher.publish(
                listOf(
                    DomainEvent.SimulationCreated(
                        simulationId = simulation.id.value,
                        simulationName = simulation.name,
                        organizationId = simulation.organization.id,
                    ),
                ),
            )
            simulation.id.value
        }
}
