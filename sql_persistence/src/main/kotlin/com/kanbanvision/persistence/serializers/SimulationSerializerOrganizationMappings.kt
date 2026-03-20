package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.domain.model.Squad
import com.kanbanvision.domain.model.Tribe
import com.kanbanvision.domain.model.Worker

internal fun Simulation.toSurrogate() =
    SimulationSurrogate(
        id = id,
        name = name,
        currentDay = currentDay.value,
        status = status.name,
        organization = organization.toSurrogate(),
        scenario = scenario.toSurrogate(),
    )

internal fun SimulationSurrogate.toDomain() =
    Simulation(
        id = id,
        name = name,
        currentDay = SimulationDay(currentDay),
        status = SimulationStatus.valueOf(status),
        organization = organization.toDomain(),
        scenario = scenario.toDomain(),
    )

private fun Organization.toSurrogate() =
    OrganizationSurrogate(
        id = id,
        name = name,
        tribes = tribes.map { it.toSurrogate() },
    )

private fun OrganizationSurrogate.toDomain() =
    Organization(
        id = id,
        name = name,
        tribes = tribes.map { it.toDomain() },
    )

private fun Tribe.toSurrogate() = TribeSurrogate(id = id, name = name, squads = squads.map { it.toSurrogate() })

private fun TribeSurrogate.toDomain() = Tribe(id = id, name = name, squads = squads.map { it.toDomain() })

private fun Squad.toSurrogate() = SquadSurrogate(id = id, name = name, workers = workers.map { it.toSurrogate() })

private fun SquadSurrogate.toDomain() = Squad(id = id, name = name, workers = workers.map { it.toDomain() })

internal fun Worker.toSurrogate() =
    WorkerSurrogate(
        id = id,
        name = name,
        abilities = abilities.map { it.toSurrogate() },
    )

internal fun WorkerSurrogate.toDomain() =
    Worker(
        id = id,
        name = name,
        abilities = abilities.map { it.toDomain() }.toSet(),
    )

private fun Ability.toSurrogate() = AbilitySurrogate(id = id, name = name.name, seniority = seniority.name)

private fun AbilitySurrogate.toDomain() =
    Ability(
        id = id,
        name = AbilityName.valueOf(name),
        seniority = Seniority.valueOf(seniority),
    )
