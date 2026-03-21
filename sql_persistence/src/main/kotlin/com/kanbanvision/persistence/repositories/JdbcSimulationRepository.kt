package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.serializers.SimulationSerializer
import com.kanbanvision.usecases.repositories.SimulationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class JdbcSimulationRepository : SimulationRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val PARAM_ID = 1
        const val PARAM_ORGANIZATION_ID = 2
        const val PARAM_WIP_LIMIT = 3
        const val PARAM_TEAM_SIZE = 4
        const val PARAM_SEED_VALUE = 5
        const val SIMULATION_NAME_ID_PREFIX_LENGTH = 8
    }

    private fun toPersistenceError(e: Throwable): DomainError {
        if (e is CancellationException) throw e
        log.error("Persistence error", e)
        return DomainError.PersistenceError(e.message ?: "Database error")
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun save(simulation: Simulation): Either<DomainError, Simulation> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        upsertSimulationRow(conn, simulation)
                        upsertSimulationState(conn, simulation)
                        conn.commit()
                    }
                    simulation
                }.mapLeft(::toPersistenceError)
        }

    override suspend fun findById(id: String): Either<DomainError, Simulation> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn -> findSimulation(conn, id) }
                }.fold(
                    ifLeft = { toPersistenceError(it).left() },
                    ifRight = { simulation -> simulation?.right() ?: DomainError.SimulationNotFound(id).left() },
                )
        }

    private fun upsertSimulationRow(
        conn: java.sql.Connection,
        simulation: Simulation,
    ) {
        conn
            .prepareStatement(
                """
                INSERT INTO simulations (id, organization_id, wip_limit, team_size, seed_value)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    organization_id = EXCLUDED.organization_id,
                    wip_limit = EXCLUDED.wip_limit,
                    team_size = EXCLUDED.team_size,
                    seed_value = EXCLUDED.seed_value
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(PARAM_ID, simulation.id)
                stmt.setString(PARAM_ORGANIZATION_ID, simulation.organization.id)
                stmt.setInt(PARAM_WIP_LIMIT, simulation.scenario.rules.wipLimit)
                stmt.setInt(PARAM_TEAM_SIZE, simulation.scenario.rules.teamSize)
                stmt.setLong(PARAM_SEED_VALUE, simulation.scenario.rules.seedValue)
                stmt.executeUpdate()
            }
    }

    private fun upsertSimulationState(
        conn: java.sql.Connection,
        simulation: Simulation,
    ) {
        conn
            .prepareStatement(
                """
                INSERT INTO simulation_states (simulation_id, state_json)
                VALUES (?, ?)
                ON CONFLICT (simulation_id) DO UPDATE SET state_json = EXCLUDED.state_json
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, simulation.id)
                stmt.setString(2, SimulationSerializer.encode(simulation))
                stmt.executeUpdate()
            }
    }

    private fun findSimulation(
        conn: java.sql.Connection,
        id: String,
    ): Simulation? =
        conn
            .prepareStatement(
                """
                SELECT s.id,
                       s.organization_id,
                       s.wip_limit,
                       s.team_size,
                       s.seed_value,
                       o.name AS organization_name,
                       ss.state_json
                FROM simulations s
                LEFT JOIN organizations o ON o.id = s.organization_id
                LEFT JOIN simulation_states ss ON ss.simulation_id = s.id
                WHERE s.id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(PARAM_ID, id)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) null else rs.toSimulation()
                }
            }

    private fun java.sql.ResultSet.toSimulation(): Simulation {
        val stateJson = getString("state_json")
        if (!stateJson.isNullOrBlank()) return SimulationSerializer.decode(stateJson)

        val organization =
            Organization(
                id = getString("organization_id"),
                name = getString("organization_name") ?: "Organization",
            )
        val rules =
            ScenarioRules.create(
                wipLimit = getInt("wip_limit"),
                teamSize = getInt("team_size"),
                seedValue = getLong("seed_value"),
            )
        val scenario = Scenario.create(name = "Default Simulation Scenario", rules = rules)
        return Simulation(
            id = getString("id"),
            name = "Simulation ${getString("id").take(SIMULATION_NAME_ID_PREFIX_LENGTH)}",
            currentDay = SimulationDay(1),
            status = SimulationStatus.DRAFT,
            organization = organization,
            scenario = scenario,
        )
    }
}
