package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.valueobjects.TenantId
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
    private val tenantRepository = JdbcTenantRepository()
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
    fun `JdbcTenantRepository findById returns PersistenceError when pool is closed`() =
        runBlocking<Unit> {
            val result = tenantRepository.findById(TenantId("any-id"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `JdbcScenarioRepository save returns PersistenceError when pool is closed`() =
        runBlocking<Unit> {
            val result = scenarioRepository.findById(ScenarioId("any-id"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `JdbcSnapshotRepository findByDay returns PersistenceError when pool is closed`() =
        runBlocking<Unit> {
            val result = snapshotRepository.findByDay(ScenarioId("any-id"), SimulationDay(1))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
