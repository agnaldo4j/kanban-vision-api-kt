package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSimulationRepositoriesErrorIntegrationTest {
    private val organizationRepository = JdbcOrganizationRepository()
    private val scenarioRepository = JdbcScenarioRepository()
    private val snapshotRepository = JdbcSnapshotRepository()

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    @BeforeEach
    fun closePool() {
        IntegrationTestSetup.closeDataSource()
    }

    @AfterEach
    fun restorePool() {
        IntegrationTestSetup.reinitDataSource()
    }

    @Test
    fun `JdbcOrganizationRepository findById returns PersistenceError when pool is closed`() =
        runBlocking<Unit> {
            val result = organizationRepository.findById("any-id")

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `JdbcScenarioRepository findById returns PersistenceError when pool is closed`() =
        runBlocking<Unit> {
            val result = scenarioRepository.findById("any-id")

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `JdbcSnapshotRepository findByDay returns PersistenceError when pool is closed`() =
        runBlocking<Unit> {
            val result = snapshotRepository.findByDay("any-id", SimulationDay(1))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
