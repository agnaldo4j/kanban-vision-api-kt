package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.Squad
import com.kanbanvision.domain.model.organization.Tribe
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationStatus

internal fun Simulation.toSurrogate() =
    SimulationSurrogate(
        id = id,
        name = name,
        currentDay = currentDay.value,
        status = status.name,
        organization = organization.toSurrogate(),
        scenario = scenario.toSurrogate(),
        decisions = decisions.map { it.toSurrogate() },
        history = history.map { it.toSurrogate() },
    )

internal fun SimulationSurrogate.toDomain() =
    Simulation(
        id = id,
        name = name,
        currentDay = SimulationDay(currentDay),
        status = SimulationStatus.valueOf(status),
        organization = organization.toDomain(),
        scenario = scenario.toDomain(),
        decisions = decisions.map { it.toDomain() },
        history = history.map { it.toDomain() },
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
