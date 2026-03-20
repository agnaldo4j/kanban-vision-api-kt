package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSnapshotRepositoryIntegrationTest {
    private val repository = JdbcSnapshotRepository()

    private val organizationId = UUID.randomUUID().toString()
    private val scenarioId = UUID.randomUUID().toString()

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    @BeforeEach
    fun cleanDatabase() {
        IntegrationTestSetup.cleanTables()
        seedOrganizationAndScenario()
    }

    private fun seedOrganizationAndScenario() {
        DatabaseFactory.dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO organizations (id, name) VALUES (?, ?)").use { stmt ->
                stmt.setString(1, organizationId)
                stmt.setString(2, "Test Org")
                stmt.executeUpdate()
            }
            conn
                .prepareStatement(
                    "INSERT INTO scenarios (id, organization_id, wip_limit, team_size, seed_value) VALUES (?, ?, ?, ?, ?)",
                ).use { stmt ->
                    stmt.setString(1, scenarioId)
                    stmt.setString(2, organizationId)
                    stmt.setInt(3, 2)
                    stmt.setInt(4, 3)
                    stmt.setLong(5, 42L)
                    stmt.executeUpdate()
                }
            conn.commit()
        }
    }

    private fun newSnapshot(day: Int) =
        DailySnapshot(
            scenarioId = scenarioId,
            day = SimulationDay(day),
            metrics = FlowMetrics(throughput = 1, wipCount = 2, blockedCount = 0, avgAgingDays = 1.5),
            movements = emptyList(),
        )

    @Test
    fun `save and findByDay round-trip returns same snapshot`() =
        runBlocking {
            val snapshot = newSnapshot(1)

            repository.save(snapshot)
            val result = repository.findByDay(scenarioId, SimulationDay(1))

            assertTrue(result.isRight())
            val found = result.getOrNull()!!
            assertEquals(snapshot.day, found.day)
            assertEquals(snapshot.metrics.throughput, found.metrics.throughput)
            assertEquals(snapshot.metrics.wipCount, found.metrics.wipCount)
        }

    @Test
    fun `findByDay returns null when no snapshot for that day`() =
        runBlocking<Unit> {
            val result = repository.findByDay(scenarioId, SimulationDay(99))

            assertTrue(result.isRight())
            assertNull(result.getOrNull())
        }

    @Test
    fun `findAllByScenario returns snapshots ordered by day`() =
        runBlocking {
            repository.save(newSnapshot(3))
            repository.save(newSnapshot(1))
            repository.save(newSnapshot(2))

            val result = repository.findAllByScenario(scenarioId)

            assertTrue(result.isRight())
            val list = result.getOrNull()!!
            assertEquals(3, list.size)
            assertEquals(1, list[0].day.value)
            assertEquals(2, list[1].day.value)
            assertEquals(3, list[2].day.value)
        }

    @Test
    fun `save overwrites existing snapshot for same day`() =
        runBlocking {
            repository.save(newSnapshot(1))
            val updated =
                newSnapshot(1).copy(
                    metrics = FlowMetrics(throughput = 5, wipCount = 1, blockedCount = 0, avgAgingDays = 0.0),
                )

            repository.save(updated)
            val result = repository.findByDay(scenarioId, SimulationDay(1))

            assertEquals(5, result.getOrNull()!!.metrics.throughput)
        }

    @Test
    fun `findAllByScenario returns empty list when no snapshots`() =
        runBlocking<Unit> {
            val result = repository.findAllByScenario(scenarioId)

            assertTrue(result.isRight())
            assertTrue(result.getOrNull()!!.isEmpty())
        }

    @Test
    fun `save and findByDay round-trip preserves movements`() =
        runBlocking {
            val movement =
                Movement(
                    type = MovementType.MOVED,
                    cardId = "item-1",
                    day = SimulationDay(1),
                    reason = "WIP available",
                )
            val snapshot =
                DailySnapshot(
                    scenarioId = scenarioId,
                    day = SimulationDay(1),
                    metrics = FlowMetrics(throughput = 1, wipCount = 1, blockedCount = 0, avgAgingDays = 0.5),
                    movements = listOf(movement),
                )

            repository.save(snapshot)
            val result = repository.findByDay(scenarioId, SimulationDay(1))

            assertTrue(result.isRight())
            val found = result.getOrNull()!!
            assertEquals(1, found.movements.size)
            assertEquals(MovementType.MOVED, found.movements.first().type)
            assertEquals(
                "item-1",
                found.movements
                    .first()
                    .cardId,
            )
            assertEquals("WIP available", found.movements.first().reason)
        }
}
