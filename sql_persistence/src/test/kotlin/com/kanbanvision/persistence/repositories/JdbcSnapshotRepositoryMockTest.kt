package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.persistence.DatabaseFactory
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Covers the conn.use { } addSuppressed path in JdbcSnapshotRepository.
class JdbcSnapshotRepositoryMockTest {
    private val repo = JdbcSnapshotRepository()

    private fun brokenDataSource(): HikariDataSource {
        val mockDs = mockk<HikariDataSource>()
        val mockConn = mockk<Connection>()
        every { mockDs.connection } returns mockConn
        every { mockConn.prepareStatement(any()) } throws SQLException("stmt error")
        every { mockConn.close() } throws SQLException("close error")
        return mockDs
    }

    private fun minimalSnapshot() =
        DailySnapshot(
            scenarioId = "s1",
            day = SimulationDay(1),
            metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0),
            movements = emptyList(),
        )

    private fun brokenStmtDataSource(): HikariDataSource {
        val mockDs = mockk<HikariDataSource>()
        val mockConn = mockk<Connection>()
        val mockStmt = mockk<PreparedStatement>()
        every { mockDs.connection } returns mockConn
        every { mockConn.prepareStatement(any()) } returns mockStmt
        every { mockStmt.setString(any(), any()) } just runs
        every { mockStmt.setInt(any(), any()) } just runs
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
                val result = repo.save(minimalSnapshot())
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `findByDay stmt close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenStmtDataSource()
                val result = repo.findByDay("s1", SimulationDay(1))
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `save conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = repo.save(minimalSnapshot())
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `findByDay conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = repo.findByDay("s1", SimulationDay(1))
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `findAllByScenario conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = repo.findAllByScenario("s1")
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }
}
