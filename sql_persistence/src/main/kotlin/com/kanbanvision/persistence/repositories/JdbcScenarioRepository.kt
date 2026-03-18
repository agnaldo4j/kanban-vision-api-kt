package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.ScenarioConfig
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.valueobjects.TenantId
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.serializers.SimulationStateSerializer
import com.kanbanvision.usecases.repositories.ScenarioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class JdbcScenarioRepository : ScenarioRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val COL_ID = 1
        const val COL_TENANT_ID = 2
        const val COL_WIP_LIMIT = 3
        const val COL_TEAM_SIZE = 4
        const val COL_SEED_VALUE = 5
    }

    private fun toPersistenceError(e: Throwable): DomainError {
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
                                INSERT INTO scenarios (id, tenant_id, wip_limit, team_size, seed_value)
                                VALUES (?, ?, ?, ?, ?)
                                ON CONFLICT (id) DO UPDATE SET
                                    wip_limit = EXCLUDED.wip_limit,
                                    team_size = EXCLUDED.team_size,
                                    seed_value = EXCLUDED.seed_value
                                """.trimIndent(),
                            ).use { stmt ->
                                stmt.setString(COL_ID, scenario.id.value)
                                stmt.setString(COL_TENANT_ID, scenario.tenantId.value)
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

    override suspend fun findById(id: ScenarioId): Either<DomainError, Scenario> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                "SELECT id, tenant_id, wip_limit, team_size, seed_value FROM scenarios WHERE id = ?",
                            ).use { stmt ->
                                stmt.setString(1, id.value)
                                stmt.executeQuery().use { rs -> if (rs.next()) rs.toScenario() else null }
                            }
                    }
                }.fold(
                    ifLeft = { toPersistenceError(it).left() },
                    ifRight = { s -> s?.right() ?: DomainError.ScenarioNotFound(id.value).left() },
                )
        }

    override suspend fun saveState(
        scenarioId: ScenarioId,
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
                                stmt.setString(1, scenarioId.value)
                                stmt.setString(2, json)
                                stmt.executeUpdate()
                            }
                        conn.commit()
                    }
                    state
                }.mapLeft(::toPersistenceError)
        }

    override suspend fun findState(scenarioId: ScenarioId): Either<DomainError, SimulationState> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                "SELECT state_json FROM scenario_states WHERE scenario_id = ?",
                            ).use { stmt ->
                                stmt.setString(1, scenarioId.value)
                                stmt.executeQuery().use { rs ->
                                    if (rs.next()) SimulationStateSerializer.decode(rs.getString("state_json")) else null
                                }
                            }
                    }
                }.fold(
                    ifLeft = { toPersistenceError(it).left() },
                    ifRight = { s -> s?.right() ?: DomainError.ScenarioNotFound(scenarioId.value).left() },
                )
        }

    private fun java.sql.ResultSet.toScenario() =
        Scenario(
            id = ScenarioId(getString("id")),
            tenantId = TenantId(getString("tenant_id")),
            config =
                ScenarioConfig(
                    wipLimit = getInt("wip_limit"),
                    teamSize = getInt("team_size"),
                    seedValue = getLong("seed_value"),
                ),
        )
}
