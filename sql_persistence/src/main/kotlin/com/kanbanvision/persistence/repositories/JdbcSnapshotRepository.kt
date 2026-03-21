package com.kanbanvision.persistence.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.serializers.DailySnapshotSerializer
import com.kanbanvision.usecases.repositories.SnapshotRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class JdbcSnapshotRepository : SnapshotRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val COL_SIMULATION_ID = 1
        const val COL_DAY = 2
        const val COL_JSON = 3
        const val PARAM_SIMULATION_ID = 1
        const val PARAM_DAY = 2
    }

    private fun toPersistenceError(e: Throwable): DomainError {
        if (e is CancellationException) throw e
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
                                INSERT INTO daily_snapshots (simulation_id, day, snapshot_json)
                                VALUES (?, ?, ?)
                                ON CONFLICT (simulation_id, day) DO UPDATE SET snapshot_json = EXCLUDED.snapshot_json
                                """.trimIndent(),
                            ).use { stmt ->
                                stmt.setString(COL_SIMULATION_ID, snapshot.simulation.id)
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
        simulationId: String,
        day: SimulationDay,
    ): Either<DomainError, DailySnapshot?> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                "SELECT snapshot_json FROM daily_snapshots WHERE simulation_id = ? AND day = ?",
                            ).use { stmt ->
                                stmt.setString(PARAM_SIMULATION_ID, simulationId)
                                stmt.setInt(PARAM_DAY, day.value)
                                stmt.executeQuery().use { rs ->
                                    if (rs.next()) DailySnapshotSerializer.decode(rs.getString("snapshot_json")) else null
                                }
                            }
                    }
                }.mapLeft(::toPersistenceError)
        }

    override suspend fun findAllBySimulation(simulationId: String): Either<DomainError, List<DailySnapshot>> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                "SELECT snapshot_json FROM daily_snapshots WHERE simulation_id = ? ORDER BY day",
                            ).use { stmt ->
                                stmt.setString(PARAM_SIMULATION_ID, simulationId)
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
