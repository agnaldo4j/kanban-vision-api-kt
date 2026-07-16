package com.kanbanvision.persistence.internal.repositories

import arrow.core.Either
import arrow.core.left
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.SimulationId
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.Scenario
import com.kanbanvision.domain.model.organization.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationStatus
import com.kanbanvision.persistence.dbQuery
import com.kanbanvision.persistence.internal.serializers.SimulationSerializer
import com.kanbanvision.persistence.internal.tables.OrganizationsTable
import com.kanbanvision.persistence.internal.tables.SimulationStatesTable
import com.kanbanvision.persistence.internal.tables.SimulationsTable
import com.kanbanvision.usecases.repositories.SimulationRepository
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

class JdbcSimulationRepository : SimulationRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val SIMULATION_NAME_ID_PREFIX_LENGTH = 8
    }

    override suspend fun save(simulation: Simulation): Either<DomainError, Simulation> =
        dbQuery(log) {
            SimulationsTable.upsert {
                it[id] = simulation.id.value
                it[organizationId] = simulation.organization.id
                it[wipLimit] = simulation.scenario.rules.wipLimit
                it[teamSize] = simulation.scenario.rules.teamSize
                it[seedValue] = simulation.scenario.rules.seedValue
            }
            SimulationStatesTable.upsert {
                it[simulationId] = simulation.id.value
                it[stateJson] = SimulationSerializer.encode(simulation)
            }
            simulation
        }

    override suspend fun findById(id: SimulationId): Either<DomainError, Simulation> =
        dbQuery(log) {
            (
                SimulationsTable
                    .join(OrganizationsTable, JoinType.INNER, SimulationsTable.organizationId, OrganizationsTable.id)
                    .join(SimulationStatesTable, JoinType.LEFT, SimulationsTable.id, SimulationStatesTable.simulationId)
            ).selectAll()
                .where(SimulationsTable.id eq id.value)
                .singleOrNull()
                ?.let { row -> rowToSimulation(row) }
        }.fold(
            ifLeft = { it.left() },
            ifRight = { simulation ->
                simulation?.let { Either.Right(it) } ?: DomainError.SimulationNotFound(id.value).left()
            },
        )

    override suspend fun findAll(
        organizationId: String,
        page: Int,
        size: Int,
    ): Either<DomainError, List<Simulation>> =
        dbQuery(log) {
            val offset = ((page - 1) * size).toLong()
            (
                SimulationsTable
                    .join(OrganizationsTable, JoinType.INNER, SimulationsTable.organizationId, OrganizationsTable.id)
                    .join(SimulationStatesTable, JoinType.LEFT, SimulationsTable.id, SimulationStatesTable.simulationId)
            ).selectAll()
                .where(SimulationsTable.organizationId eq organizationId)
                .orderBy(SimulationsTable.id to SortOrder.ASC)
                .limit(size)
                .offset(offset)
                .map { row -> rowToSimulation(row) }
        }

    override suspend fun countByOrganization(organizationId: String): Either<DomainError, Long> =
        dbQuery(log) {
            SimulationsTable
                .selectAll()
                .where(SimulationsTable.organizationId eq organizationId)
                .count()
        }

    private fun rowToSimulation(row: ResultRow): Simulation {
        val stateJson = row.getOrNull(SimulationStatesTable.stateJson)
        if (!stateJson.isNullOrBlank()) return SimulationSerializer.decode(stateJson)
        return buildFallbackSimulation(row)
    }

    private fun buildFallbackSimulation(row: ResultRow): Simulation {
        val organization =
            Organization(
                id = row[SimulationsTable.organizationId],
                name = row[OrganizationsTable.name],
            )
        val rules =
            ScenarioRules.create(
                wipLimit = row[SimulationsTable.wipLimit],
                teamSize = row[SimulationsTable.teamSize],
                seedValue = row[SimulationsTable.seedValue],
            )
        val scenario = Scenario.create(name = "Default Simulation Scenario", rules = rules)
        return Simulation(
            id = SimulationId(row[SimulationsTable.id]),
            name = "Simulation ${row[SimulationsTable.id].take(SIMULATION_NAME_ID_PREFIX_LENGTH)}",
            currentDay = SimulationDay(1),
            status = SimulationStatus.DRAFT,
            organization = organization,
            scenario = scenario,
        )
    }
}
