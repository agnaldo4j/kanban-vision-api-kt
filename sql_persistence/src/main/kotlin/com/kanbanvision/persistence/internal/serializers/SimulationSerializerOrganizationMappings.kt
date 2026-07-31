package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.Squad
import com.kanbanvision.domain.model.organization.Tribe
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.domain.model.simulation.SimulationStatus

internal fun Simulation.toSurrogate() =
    SimulationSurrogate(
        id = id.value,
        name = name.value,
        currentDay = currentDay.value,
        status = status.name,
        organization = organization.toSurrogate(),
        scenario = scenario.toSurrogate(),
        decisions = decisions.map { it.toSurrogate() },
        history = history.map { it.toSurrogate() },
    )

internal fun SimulationSurrogate.toDomain() =
    Simulation(
        id = SimulationId(decodeId(id)),
        name = decodeName(name),
        currentDay = decodeDay(currentDay),
        status = decodeEnum(status, SimulationStatus.DRAFT),
        organization = organization.toDomain(),
        scenario = scenario.toDomain(),
        decisions = decisions.map { it.toDomain() },
        history = history.map { it.toDomain() },
    )

private fun Organization.toSurrogate() =
    OrganizationSurrogate(
        id = id,
        name = name.value,
        tribes = tribes.map { it.toSurrogate() },
    )

private fun OrganizationSurrogate.toDomain() =
    Organization(
        id = decodeId(id),
        name = decodeName(name),
        tribes = tribes.map { it.toDomain() },
    )

private fun Tribe.toSurrogate() = TribeSurrogate(id = id, name = name.value, squads = squads.map { it.toSurrogate() })

private fun TribeSurrogate.toDomain() = Tribe(id = decodeId(id), name = decodeName(name), squads = squads.map { it.toDomain() })

private fun Squad.toSurrogate() = SquadSurrogate(id = id, name = name.value, workers = workers.map { it.toSurrogate() })

private fun SquadSurrogate.toDomain() = Squad(id = decodeId(id), name = decodeName(name), workers = workers.map { it.toDomain() })

internal fun Worker.toSurrogate() =
    WorkerSurrogate(
        id = id,
        name = name.value,
        abilities = abilities.map { it.toSurrogate() },
    )

internal fun WorkerSurrogate.toDomain(requiredAbility: AbilityName? = null) =
    Worker(
        id = decodeId(id),
        name = decodeName(name),
        abilities = abilities.map { it.toDomain() }.toSet().completedForWorkerInvariants(requiredAbility),
    )

private fun Set<Ability>.completedForWorkerInvariants(requiredAbility: AbilityName?): Set<Ability> {
    val withStepAbility =
        if (requiredAbility != null && none { it.name == requiredAbility }) {
            this + Ability(name = requiredAbility, seniority = Seniority.JR)
        } else {
            this
        }
    val nonEmpty = withStepAbility.ifEmpty { setOf(Ability(name = CROSS_FIELD_NEUTRAL_ABILITY, seniority = Seniority.JR)) }
    val hasTester = nonEmpty.any { it.name == AbilityName.TESTER }
    val hasDeployer = nonEmpty.any { it.name == AbilityName.DEPLOYER }
    return if (hasTester && !hasDeployer) {
        nonEmpty + Ability(name = AbilityName.DEPLOYER, seniority = Seniority.JR)
    } else {
        nonEmpty
    }
}

private fun Ability.toSurrogate() = AbilitySurrogate(id = id, name = name.name, seniority = seniority.name)

private fun AbilitySurrogate.toDomain() =
    Ability(
        id = decodeId(id),
        name = decodeEnum(name, CROSS_FIELD_NEUTRAL_ABILITY),
        seniority = decodeEnum(seniority, Seniority.JR),
    )
