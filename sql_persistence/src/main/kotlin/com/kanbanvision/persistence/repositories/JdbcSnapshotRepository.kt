package com.kanbanvision.persistence.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.DailySnapshot
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.serializers.DailySnapshotSerializer
import com.kanbanvision.usecases.repositories.SnapshotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class JdbcSnapshotRepository : SnapshotRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val COL_SCENARIO_ID = 1
        const val COL_DAY = 2
        const val COL_JSON = 3
    }

    private fun toPersistenceError(e: Throwable): DomainError {
        log.error("Persistence error", e)
        return DomainError.PersistenceError(e.message ?: "Database error")
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun save(snapshot: DailySnapshot): Either<DomainError, DailySnapshot> =
        query {
            Either
                .catch {
                    val json = DailySnapshotSerializer.encode(snapshot)
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                """
                                INSERT INTO daily_snapshots (scenario_id, day, snapshot_json)
                                VALUES (?, ?, ?)
                                ON CONFLICT (scenario_id, day) DO UPDATE SET snapshot_json = EXCLUDED.snapshot_json
                                """.trimIndent(),
                            ).use { stmt ->
                                stmt.setString(COL_SCENARIO_ID, snapshot.scenarioId.value)
                                stmt.setInt(COL_DAY, snapshot.day.value)
                                stmt.setString(COL_JSON, json)
                                stmt.executeUpdate()
                            }
                        conn.commit()
                    }
                    snapshot
                }.mapLeft(::toPersistenceError)
        }

    override suspend fun findByDay(
        scenarioId: ScenarioId,
        day: SimulationDay,
    ): Either<DomainError, DailySnapshot?> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                "SELECT snapshot_json FROM daily_snapshots WHERE scenario_id = ? AND day = ?",
                            ).use { stmt ->
                                stmt.setString(1, scenarioId.value)
                                stmt.setInt(2, day.value)
                                stmt.executeQuery().use { rs ->
                                    if (rs.next()) DailySnapshotSerializer.decode(rs.getString("snapshot_json")) else null
                                }
                            }
                    }
                }.mapLeft(::toPersistenceError)
        }

    override suspend fun findAllByScenario(scenarioId: ScenarioId): Either<DomainError, List<DailySnapshot>> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                "SELECT snapshot_json FROM daily_snapshots WHERE scenario_id = ? ORDER BY day",
                            ).use { stmt ->
                                stmt.setString(1, scenarioId.value)
                                stmt.executeQuery().use { rs ->
                                    buildList {
                                        while (rs.next()) add(DailySnapshotSerializer.decode(rs.getString("snapshot_json")))
                                    }
                                }
                            }
                    }
                }.mapLeft(::toPersistenceError)
        }
}
