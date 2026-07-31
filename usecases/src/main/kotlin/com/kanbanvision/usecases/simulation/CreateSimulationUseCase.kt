package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.common.errors.DomainError
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.simulation.events.DomainEvent
import com.kanbanvision.usecases.ports.EventPublisherPort
import com.kanbanvision.usecases.repositories.OrganizationRepository
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.simulation.commands.CreateSimulationCommand
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

class CreateSimulationUseCase(
    private val organizationRepository: OrganizationRepository,
    private val simulationRepository: SimulationRepository,
    private val publisher: EventPublisherPort,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
            val simulation = Simulation.draftFor(organization = organization, rules = rules)

            val (id, duration) = timed { persist(simulation, clock.instant()) }
            log.info("Simulation created: id={} duration={}ms", id, duration.inWholeMilliseconds)
            id
        }

    private suspend fun persist(
        simulation: Simulation,
        now: Instant,
    ): Either<DomainError, String> =
        either {
            simulationRepository.save(simulation).bind()
            publisher.publish(
                listOf(
                    DomainEvent.SimulationCreated(
                        simulationId = simulation.id.value,
                        simulationName = simulation.name.value,
                        organizationId = simulation.organization.id,
                        occurredAt = now,
                    ),
                ),
            )
            simulation.id.value
        }
}
