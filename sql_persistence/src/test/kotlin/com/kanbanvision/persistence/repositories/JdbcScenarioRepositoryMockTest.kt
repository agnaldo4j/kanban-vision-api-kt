package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.ScenarioConfig
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.valueobjects.TenantId
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.IntegrationTestSetup
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Covers the conn.use { } addSuppressed path: body throws (prepareStatement)
// AND conn.close() also throws — exercises the suppressed-exception branch in Kotlin's use { }.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcScenarioRepositoryMockTest {
    private val repo = JdbcScenarioRepository()
    private val config = ScenarioConfig(wipLimit = 3, teamSize = 2, seedValue = 42L)

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    private fun brokenDataSource(): HikariDataSource {
        val mockDs = mockk<HikariDataSource>()
        val mockConn = mockk<Connection>()
        every { mockDs.connection } returns mockConn
        every { mockConn.prepareStatement(any()) } throws SQLException("stmt error")
        every { mockConn.close() } throws SQLException("close error")
        return mockDs
    }

    private fun brokenStmtDataSource(): HikariDataSource {
        val mockDs = mockk<HikariDataSource>()
        val mockConn = mockk<Connection>()
        val mockStmt = mockk<PreparedStatement>()
        every { mockDs.connection } returns mockConn
        every { mockConn.prepareStatement(any()) } returns mockStmt
        every { mockStmt.setString(any(), any()) } just runs
        every { mockStmt.setInt(any(), any()) } just runs
        every { mockStmt.setLong(any(), any()) } just runs
        every { mockStmt.executeUpdate() } throws SQLException("update error")
        every { mockStmt.executeQuery() } throws SQLException("query error")
        every { mockStmt.close() } throws SQLException("stmt close error")
        every { mockConn.close() } just runs
        return mockDs
    }

    @Test
    fun `save stmt close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenStmtDataSource()
                val scenario = Scenario(ScenarioId("s1"), TenantId("t1"), config)
                val result = repo.save(scenario)
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `findById stmt close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenStmtDataSource()
                val result = repo.findById(ScenarioId("s1"))
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `save conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val scenario = Scenario(ScenarioId("s1"), TenantId("t1"), config)
                val result = repo.save(scenario)
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `findById conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = repo.findById(ScenarioId("s1"))
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `saveState conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val state = SimulationState.initial(config)
                val result = repo.saveState(ScenarioId("s1"), state)
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `findState conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = repo.findState(ScenarioId("s1"))
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }
}
