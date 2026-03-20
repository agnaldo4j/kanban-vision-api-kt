package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Step
import com.kanbanvision.persistence.DatabaseFactory
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Covers the conn.use { } addSuppressed path in Board, Step, and Organization repositories.
class JdbcBoardStepOrganizationRepositoryMockTest {
    private val boardRepo = JdbcBoardRepository()
    private val stepRepo = JdbcStepRepository()
    private val organizationRepo = JdbcOrganizationRepository()

    private fun brokenDataSource(): HikariDataSource {
        val mockDs = mockk<HikariDataSource>()
        val mockConn = mockk<Connection>()
        every { mockDs.connection } returns mockConn
        every { mockConn.prepareStatement(any()) } throws SQLException("stmt error")
        every { mockConn.close() } throws SQLException("close error")
        return mockDs
    }

    @Test
    fun `Board save conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val board = Board(id = "b1", name = "Test Board")
                val result = boardRepo.save(board)
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `Board findById conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = boardRepo.findById("b1")
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `Step save conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val step = Step("c1", "b1", "Todo", 0, AbilityName.PRODUCT_MANAGER)
                val result = stepRepo.save(step)
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `Step findById conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = stepRepo.findById("c1")
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `Step findByBoardId conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = stepRepo.findByBoardId("b1")
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }

    @Test
    fun `Organization findById conn close suppresses exception`() =
        runBlocking {
            mockkObject(DatabaseFactory) {
                every { DatabaseFactory.dataSource } returns brokenDataSource()
                val result = organizationRepo.findById("t1")
                assertTrue(result.isLeft())
                assertIs<DomainError.PersistenceError>(result.leftOrNull())
            }
        }
}
