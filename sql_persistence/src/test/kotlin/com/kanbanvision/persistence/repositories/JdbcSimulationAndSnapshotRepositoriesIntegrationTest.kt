package com.kanbanvision.persistence.repositories

import arrow.core.getOrElse
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.persistence.support.EmbeddedPostgresSupport
import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSimulationAndSnapshotRepositoriesIntegrationTest {
    private val simulationRepository = JdbcSimulationRepository()
    private val snapshotRepository = JdbcSnapshotRepository()
    private val organizationRepository = JdbcOrganizationRepository()

    @BeforeAll
    fun setupDatabase() {
        EmbeddedPostgresSupport.ensureStarted()
    }

    @BeforeEach
    fun cleanDatabase() {
        EmbeddedPostgresSupport.refreshDataSource()
        EmbeddedPostgresSupport.resetDatabase()
    }

    @Test
    fun `given simulation aggregate when saving and finding by id then repository restores serialized state`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            EmbeddedPostgresSupport.insertOrganization(simulation.organization.id, simulation.organization.name)

            simulationRepository.save(simulation).getOrElse { error("save simulation failed: $it") }
            val loaded = simulationRepository.findById(simulation.id).getOrElse { error("find simulation failed: $it") }

            assertEquals(simulation.id, loaded.id)
            assertEquals(simulation.name, loaded.name)
            assertEquals(SimulationStatus.RUNNING, loaded.status)
            assertEquals(
                simulation.scenario.board.steps
                    .first()
                    .cards
                    .first()
                    .id,
                loaded.scenario.board.steps
                    .first()
                    .cards
                    .first()
                    .id,
            )
        }

    @Test
    fun `given existing simulation id when saving again then repository upserts simulation row and state`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            EmbeddedPostgresSupport.insertOrganization(simulation.organization.id, simulation.organization.name)
            simulationRepository.save(simulation).getOrElse { error("first save simulation failed: $it") }

            val changed = simulation.copy(name = "Simulation Updated", currentDay = SimulationDay(4))
            simulationRepository.save(changed).getOrElse { error("second save simulation failed: $it") }

            val loaded = simulationRepository.findById(simulation.id).getOrElse { error("find simulation failed: $it") }
            assertEquals("Simulation Updated", loaded.name)
            assertEquals(4, loaded.currentDay.value)
        }

    @Test
    fun `given simulation row without serialized state when finding by id then repository builds fallback simulation`() =
        runBlocking {
            val simulationId = "04000000-0000-0000-0000-000000000001"
            val organizationId = "04000000-0000-0000-0000-000000000002"
            EmbeddedPostgresSupport.insertOrganization(organizationId, "Fallback Org")
            EmbeddedPostgresSupport.insertSimulationRow(
                EmbeddedPostgresSupport.SimulationSeed(
                    id = simulationId,
                    organizationId = organizationId,
                    wipLimit = 3,
                    teamSize = 4,
                    seedValue = 12L,
                ),
            )

            val loaded = simulationRepository.findById(simulationId).getOrElse { error("find fallback simulation failed: $it") }

            assertEquals(simulationId, loaded.id)
            assertEquals("Fallback Org", loaded.organization.name)
            assertEquals(1, loaded.currentDay.value)
            assertEquals(3, loaded.scenario.rules.wipLimit)
        }

    @Test
    fun `given unknown simulation id when finding by id then repository returns simulation not found domain error`() =
        runBlocking {
            val result = simulationRepository.findById("04000000-0000-0000-0000-000000009999")
            assertIs<DomainError.SimulationNotFound>(result.leftOrNull())
        }

    @Test
    fun `given persisted organization when finding by id then organization data is returned`() =
        runBlocking {
            val organizationId = "05000000-0000-0000-0000-000000000001"
            EmbeddedPostgresSupport.insertOrganization(organizationId, "Org persisted")

            val found = organizationRepository.findById(organizationId).getOrElse { error("find org failed: $it") }

            assertEquals(organizationId, found.id)
            assertEquals("Org persisted", found.name)
        }

    @Test
    fun `given persisted daily snapshots when querying by day and simulation then repository returns filtered and ordered data`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            val dayTwo = PersistenceFixtures.snapshot(simulation.id, day = 2)
            val dayThree = PersistenceFixtures.snapshot(simulation.id, day = 3).copy(id = "c0000000-0000-0000-0000-000000000099")
            EmbeddedPostgresSupport.insertOrganization(simulation.organization.id, simulation.organization.name)
            simulationRepository.save(simulation).getOrElse { error("save simulation failed: $it") }

            snapshotRepository.save(dayTwo).getOrElse { error("save dayTwo failed: $it") }
            snapshotRepository.save(dayThree).getOrElse { error("save dayThree failed: $it") }

            val byDay = snapshotRepository.findByDay(simulation.id, SimulationDay(2)).getOrElse { error("find day failed: $it") }
            val all = snapshotRepository.findAllBySimulation(simulation.id).getOrElse { error("find all failed: $it") }

            assertNotNull(byDay)
            assertEquals(dayTwo.id, byDay.id)
            assertEquals(listOf(2, 3), all.map { it.day.value })
        }

    @Test
    fun `given same snapshot day when saving again then repository upserts latest snapshot payload`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            val dayTwo = PersistenceFixtures.snapshot(simulation.id, day = 2)
            val changedDayTwo = dayTwo.copy(id = "c0000000-0000-0000-0000-000000000222")
            EmbeddedPostgresSupport.insertOrganization(simulation.organization.id, simulation.organization.name)
            simulationRepository.save(simulation).getOrElse { error("save simulation failed: $it") }

            snapshotRepository.save(dayTwo).getOrElse { error("first snapshot save failed: $it") }
            snapshotRepository.save(changedDayTwo).getOrElse { error("second snapshot save failed: $it") }

            val loaded = snapshotRepository.findByDay(simulation.id, SimulationDay(2)).getOrElse { error("find day failed: $it") }
            assertNotNull(loaded)
            assertEquals(changedDayTwo.id, loaded.id)
        }

    @Test
    fun `given simulation without snapshots when finding by day then repository returns null without error`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            EmbeddedPostgresSupport.insertOrganization(simulation.organization.id, simulation.organization.name)
            simulationRepository.save(simulation).getOrElse { error("save simulation failed: $it") }

            val byDay = snapshotRepository.findByDay(simulation.id, SimulationDay(9)).getOrElse { error("find day failed: $it") }
            val all = snapshotRepository.findAllBySimulation(simulation.id).getOrElse { error("find all failed: $it") }

            assertEquals(null, byDay)
            assertEquals(emptyList(), all)
        }

    @Test
    fun `given unknown simulation id when listing snapshots then repository returns empty list`() =
        runBlocking {
            val all =
                snapshotRepository
                    .findAllBySimulation("05000000-0000-0000-0000-000000009998")
                    .getOrElse { error("find all failed: $it") }
            assertEquals(emptyList(), all)
        }

    @Test
    fun `given unknown organization id when finding organization then repository returns organization not found domain error`() =
        runBlocking {
            val result = organizationRepository.findById("05000000-0000-0000-0000-000000009999")
            assertIs<DomainError.OrganizationNotFound>(result.leftOrNull())
        }
}
