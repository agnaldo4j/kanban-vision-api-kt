package com.kanbanvision.persistence.internal.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.support.EmbeddedPostgresSupport
import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoriesErrorHandlingTest {
    private val organizationRepository = JdbcOrganizationRepository()
    private val simulationRepository = JdbcSimulationRepository()
    private val snapshotRepository = JdbcSnapshotRepository()

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
    fun `given closed datasource when organization and simulation methods execute then persistence errors are returned`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            DatabaseFactory.dataSource.close()
            assertPersistenceError(organizationRepository.findById("06000000-0000-0000-0000-000000000004").leftOrNull())
            assertPersistenceError(simulationRepository.save(simulation).leftOrNull())
            assertPersistenceError(simulationRepository.findById(simulation.id).leftOrNull())
        }

    @Test
    fun `given closed datasource when snapshot methods execute then persistence errors are returned`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            val snapshot = PersistenceFixtures.snapshot(simulation.id.value)
            DatabaseFactory.dataSource.close()
            assertPersistenceError(snapshotRepository.save(snapshot).leftOrNull())
            assertPersistenceError(snapshotRepository.findByDay(simulation.id, SimulationDay(1)).leftOrNull())
            assertPersistenceError(snapshotRepository.findAllBySimulation(simulation.id).leftOrNull())
        }

    private fun assertPersistenceError(error: DomainError?) {
        assertIs<DomainError.PersistenceError>(error)
    }
}
