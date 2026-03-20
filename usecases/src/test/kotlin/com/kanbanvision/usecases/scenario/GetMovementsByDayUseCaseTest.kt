package com.kanbanvision.usecases.scenario

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.queries.GetMovementsByDayQuery
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetMovementsByDayUseCaseTest {
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val useCase = GetMovementsByDayUseCase(snapshotRepository)

    private val scenarioId = "scenario-1"
    private val movement =
        Movement(
            type = MovementType.MOVED,
            cardId = "item-1",
            day = SimulationDay(1),
            reason = "WIP available",
        )
    private val snapshot =
        DailySnapshot(
            scenarioId = scenarioId,
            day = SimulationDay(1),
            metrics = FlowMetrics(throughput = 1, wipCount = 1, blockedCount = 0, avgAgingDays = 1.0),
            movements = listOf(movement),
        )

    @Test
    fun `execute returns movements when snapshot found`() =
        runTest {
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns snapshot.right()

            val result = useCase.execute(GetMovementsByDayQuery(scenarioId = scenarioId, day = 1))

            assertTrue(result.isRight())
            val movements = result.getOrNull()!!
            assertEquals(1, movements.size)
            assertEquals(MovementType.MOVED, movements.first().type)
        }

    @Test
    fun `execute returns empty list when snapshot has no movements`() =
        runTest {
            val emptySnapshot = snapshot.copy(movements = emptyList())
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns emptySnapshot.right()

            val result = useCase.execute(GetMovementsByDayQuery(scenarioId = scenarioId, day = 1))

            assertTrue(result.isRight())
            assertEquals(emptyList(), result.getOrNull())
        }

    @Test
    fun `execute returns ScenarioNotFound when snapshot does not exist`() =
        runTest {
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns null.right()

            val result = useCase.execute(GetMovementsByDayQuery(scenarioId = scenarioId, day = 1))

            assertTrue(result.isLeft())
            assertIs<DomainError.ScenarioNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns PersistenceError when repository fails`() =
        runTest {
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns
                DomainError.PersistenceError("DB down").left()

            val result = useCase.execute(GetMovementsByDayQuery(scenarioId = scenarioId, day = 1))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `execute returns ValidationError when query is invalid`() =
        runTest {
            val result = useCase.execute(GetMovementsByDayQuery(scenarioId = "", day = 0))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }
}
