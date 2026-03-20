package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioConfig
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.serializers.SimulationStateSerializer
import com.kanbanvision.usecases.repositories.ScenarioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class JdbcScenarioRepository : ScenarioRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val COL_ID = 1
        const val COL_ORGANIZATION_ID = 2
        const val COL_WIP_LIMIT = 3
        const val COL_TEAM_SIZE = 4
        const val COL_SEED_VALUE = 5
    }

    private fun toPersistenceError(e: Throwable): DomainError {
        if (e is CancellationException) throw e
        log.error("Persistence error", e)
        return DomainError.PersistenceError(e.message ?: "Database error")
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun save(scenario: Scenario): Either<DomainError, Scenario> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                """
                                INSERT INTO scenarios (id, organization_id, wip_limit, team_size, seed_value)
                                VALUES (?, ?, ?, ?, ?)
                                ON CONFLICT (id) DO UPDATE SET
                                    wip_limit = EXCLUDED.wip_limit,
                                    team_size = EXCLUDED.team_size,
                                    seed_value = EXCLUDED.seed_value
                                """.trimIndent(),
                            ).use { stmt ->
                                stmt.setString(COL_ID, scenario.id)
                                stmt.setString(COL_ORGANIZATION_ID, scenario.organizationId)
                                stmt.setInt(COL_WIP_LIMIT, scenario.config.wipLimit)
                                stmt.setInt(COL_TEAM_SIZE, scenario.config.teamSize)
                                stmt.setLong(COL_SEED_VALUE, scenario.config.seedValue)
                                stmt.executeUpdate()
                            }
                        conn.commit()
                    }
                    scenario
                }.mapLeft(::toPersistenceError)
        }

    override suspend fun findById(id: String): Either<DomainError, Scenario> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                "SELECT id, organization_id, wip_limit, team_size, seed_value FROM scenarios WHERE id = ?",
                            ).use { stmt ->
                                stmt.setString(1, id)
                                stmt.executeQuery().use { rs -> if (rs.next()) rs.toScenario() else null }
                            }
                    }
                }.fold(
                    ifLeft = { toPersistenceError(it).left() },
                    ifRight = { s -> s?.right() ?: DomainError.ScenarioNotFound(id).left() },
                )
        }

    override suspend fun saveState(
        scenarioId: String,
        state: SimulationState,
    ): Either<DomainError, SimulationState> =
        query {
            Either
                .catch {
                    val json = SimulationStateSerializer.encode(state)
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                """
                                INSERT INTO scenario_states (scenario_id, state_json)
                                VALUES (?, ?)
                                ON CONFLICT (scenario_id) DO UPDATE SET state_json = EXCLUDED.state_json
                                """.trimIndent(),
                            ).use { stmt ->
                                stmt.setString(1, scenarioId)
                                stmt.setString(2, json)
                                stmt.executeUpdate()
                            }
                        conn.commit()
                    }
                    state
                }.mapLeft(::toPersistenceError)
        }

    override suspend fun findState(scenarioId: String): Either<DomainError, SimulationState> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                "SELECT state_json FROM scenario_states WHERE scenario_id = ?",
                            ).use { stmt ->
                                stmt.setString(1, scenarioId)
                                stmt.executeQuery().use { rs ->
                                    if (rs.next()) SimulationStateSerializer.decode(rs.getString("state_json")) else null
                                }
                            }
                    }
                }.fold(
                    ifLeft = { toPersistenceError(it).left() },
                    ifRight = { s -> s?.right() ?: DomainError.ScenarioNotFound(scenarioId).left() },
                )
        }

    private fun java.sql.ResultSet.toScenario() =
        Scenario(
            id = getString("id"),
            organizationId = getString("organization_id"),
            config =
                ScenarioConfig(
                    wipLimit = getInt("wip_limit"),
                    teamSize = getInt("team_size"),
                    seedValue = getLong("seed_value"),
                ),
        )
}
