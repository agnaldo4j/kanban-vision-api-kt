package com.kanbanvision.persistence.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.persistence.dbQuery
import com.kanbanvision.persistence.serializers.DailySnapshotSerializer
import com.kanbanvision.persistence.tables.DailySnapshotsTable
import com.kanbanvision.usecases.repositories.SnapshotRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

class JdbcSnapshotRepository : SnapshotRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun save(snapshot: DailySnapshot): Either<DomainError, DailySnapshot> =
        dbQuery(log) {
            val json = DailySnapshotSerializer.encode(snapshot)
            DailySnapshotsTable.upsert {
                it[simulationId] = snapshot.simulation.id
                it[day] = snapshot.day.value
                it[snapshotJson] = json
            }
            snapshot
        }

    override suspend fun findByDay(
        simulationId: String,
        day: SimulationDay,
    ): Either<DomainError, DailySnapshot?> =
        dbQuery(log) {
            DailySnapshotsTable
                .selectAll()
                .where((DailySnapshotsTable.simulationId eq simulationId) and (DailySnapshotsTable.day eq day.value))
                .singleOrNull()
                ?.let { row -> DailySnapshotSerializer.decode(row[DailySnapshotsTable.snapshotJson]) }
        }

    override suspend fun findAllBySimulation(simulationId: String): Either<DomainError, List<DailySnapshot>> =
        dbQuery(log) {
            DailySnapshotsTable
                .selectAll()
                .where(DailySnapshotsTable.simulationId eq simulationId)
                .orderBy(DailySnapshotsTable.day)
                .map { row -> DailySnapshotSerializer.decode(row[DailySnapshotsTable.snapshotJson]) }
        }
}
