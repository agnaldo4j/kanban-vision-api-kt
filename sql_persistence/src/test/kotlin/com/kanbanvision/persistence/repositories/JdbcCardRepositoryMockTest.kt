package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Card
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
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Covers the conn.use { } addSuppressed path in JdbcCardRepository.
class JdbcCardRepositoryMockTest {
    private val repo = JdbcCardRepository()

    private fun brokenDataSource(): HikariDataSource {
        val mockDs = mockk<HikariDataSource>()
        val mockConn = mockk<Connection>()
        every { mockDs.connection } returns mockConn
        every { mockConn.prepareStatement(any()) } throws SQLException("stmt error")
        every { mockConn.close() } throws SQLException("close error")
        return mockDs
    }

    private fun minimalCard() =
        Card(
            id = "card1",
            columnId = "col1",
            title = "Task",
            description = "",
            position = 0,
            audit = Audit(createdAt = Instant.now()),
        )

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
                val result = repo.save(minimalCard())
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `save conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = repo.save(minimalCard())
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `findById conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = repo.findById("card1")
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `findByColumnId conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = repo.findByColumnId("col1")
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `updateCard conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = repo.updateCard("card1") { it }
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    private fun stubSelectReturningCard(
        mockConn: Connection,
        mockSelectStmt: PreparedStatement,
    ) {
        val mockRs = mockk<ResultSet>()
        every { mockConn.prepareStatement(match { it.contains("FOR UPDATE") }) } returns mockSelectStmt
        every { mockSelectStmt.setString(any(), any()) } just runs
        every { mockSelectStmt.executeQuery() } returns mockRs
        every { mockRs.next() } returns true
        every { mockRs.getString("id") } returns "card1"
        every { mockRs.getString("step_id") } returns "col1"
        every { mockRs.getString("title") } returns "Task"
        every { mockRs.getString("description") } returns ""
        every { mockRs.getInt("position") } returns 0
        every { mockRs.getLong("created_at") } returns Instant.now().toEpochMilli()
        every { mockRs.close() } just runs
        every { mockSelectStmt.close() } just runs
    }

    @Test
    fun `updateCard returns PersistenceError when executeUpdate returns zero rows`() =
        runBlocking {
            val mockDs = mockk<HikariDataSource>()
            val mockConn = mockk<Connection>()
            val mockUpdateStmt = mockk<PreparedStatement>()
            stubSelectReturningCard(mockConn, mockk())
            every { mockDs.connection } returns mockConn
            every { mockConn.prepareStatement(match { it.contains("UPDATE cards") }) } returns mockUpdateStmt
            every { mockUpdateStmt.setString(any(), any()) } just runs
            every { mockUpdateStmt.setInt(any(), any()) } just runs
            every { mockUpdateStmt.executeUpdate() } returns 0
            every { mockUpdateStmt.close() } just runs
            every { mockConn.close() } just runs
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns mockDs
                val result = repo.updateCard("card1") { it }
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }
}
