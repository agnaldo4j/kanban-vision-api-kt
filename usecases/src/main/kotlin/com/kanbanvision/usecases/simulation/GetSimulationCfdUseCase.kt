package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.queries.GetSimulationCfdQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetSimulationCfdUseCase(
    private val snapshotRepository: SnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetSimulationCfdQuery): Either<DomainError, CfdResult> =
        either {
            query.validate().bind()
            val id = query.simulationId
            val (snapshots, duration) = timed { snapshotRepository.findAllBySimulation(id) }
            val result = buildCfd(id, snapshots)
            log.info(
                "CFD computed: id={} days={} duration={}ms",
                id,
                snapshots.size,
                duration.inWholeMilliseconds,
            )
            result
        }

    private fun buildCfd(
        simulationId: String,
        snapshots: List<DailySnapshot>,
    ): CfdResult {
        var cumulativeThroughput = 0
        val series =
            snapshots
                .sortedBy { it.day.value }
                .map { snapshot ->
                    cumulativeThroughput += snapshot.metrics.throughput
                    CfdDataPoint(
                        day = snapshot.day.value,
                        throughputCumulative = cumulativeThroughput,
                        wipCount = snapshot.metrics.wipCount,
                        blockedCount = snapshot.metrics.blockedCount,
                    )
                }
        return CfdResult(simulationId = simulationId, series = series)
    }
}
