package com.kanbanvision.usecases.scenario

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.queries.GetFlowMetricsRangeQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

data class DailyMetrics(
    val day: Int,
    val metrics: FlowMetrics,
)

class GetFlowMetricsRangeUseCase(
    private val snapshotRepository: SnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetFlowMetricsRangeQuery): Either<DomainError, List<DailyMetrics>> =
        either {
            query.validate().bind()
            val id = query.scenarioId
            val (snapshots, duration) = timed { snapshotRepository.findAllByScenario(id) }
            val result =
                snapshots
                    .filter { it.day.value in query.fromDay..query.toDay }
                    .sortedBy { it.day.value }
                    .map { DailyMetrics(day = it.day.value, metrics = it.metrics) }
            log.info(
                "FlowMetrics range fetched: scenario={} from={} to={} days={} duration={}ms",
                id,
                query.fromDay,
                query.toDay,
                result.size,
                duration.inWholeMilliseconds,
            )
            result
        }
}
