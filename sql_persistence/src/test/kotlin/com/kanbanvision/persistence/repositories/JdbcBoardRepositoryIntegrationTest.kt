package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcBoardRepositoryIntegrationTest {
    private val repository = JdbcBoardRepository()

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    @BeforeEach
    fun cleanDatabase() {
        IntegrationTestSetup.cleanTables()
    }

    private fun newBoard(name: String = "Test Board") =
        Board(
            id = BoardId(UUID.randomUUID().toString()),
            name = name,
            createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),
        )

    @Test
    fun `save persists board and findById returns it`() =
        runBlocking {
            val board = newBoard()

            repository.save(board)

            val result = repository.findById(board.id)
            assertTrue(result.isRight())
            val found = result.getOrNull()
            assertNotNull(found)
            assertEquals(board.id, found.id)
            assertEquals(board.name, found.name)
            assertEquals(board.createdAt, found.createdAt)
        }

    @Test
    fun `findById returns BoardNotFound when board does not exist`() =
        runBlocking<Unit> {
            val result = repository.findById(BoardId(UUID.randomUUID().toString()))

            assertTrue(result.isLeft())
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
        }

    @Test
    fun `save updates existing board name on conflict`() =
        runBlocking {
            val board = newBoard("Original Name")
            repository.save(board)

            repository.save(board.copy(name = "Updated Name"))

            val result = repository.findById(board.id)
            assertEquals("Updated Name", result.getOrNull()?.name)
        }

    @Test
    fun `findAll returns all saved boards`() =
        runBlocking {
            repository.save(newBoard("Board One"))
            repository.save(newBoard("Board Two"))

            val result = repository.findAll()

            assertEquals(2, result.getOrNull()?.size)
        }

    @Test
    fun `findAll returns empty list when no boards exist`() =
        runBlocking {
            val result = repository.findAll()

            assertTrue(result.isRight())
            assertTrue(result.getOrNull()!!.isEmpty())
        }

    @Test
    fun `delete removes board and returns true`() =
        runBlocking<Unit> {
            val board = newBoard()
            repository.save(board)

            val deleted = repository.delete(board.id)

            assertTrue(deleted.getOrNull() == true)
            val found = repository.findById(board.id)
            assertTrue(found.isLeft())
            assertIs<DomainError.BoardNotFound>(found.leftOrNull())
        }

    @Test
    fun `delete returns false when board does not exist`() =
        runBlocking {
            val deleted = repository.delete(BoardId(UUID.randomUUID().toString()))

            assertFalse(deleted.getOrNull() ?: true)
        }

    @Test
    fun `save with name exceeding column limit returns PersistenceError`() =
        runBlocking<Unit> {
            val board =
                Board(
                    id = BoardId(UUID.randomUUID().toString()),
                    name = "x".repeat(256),
                    createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),
                )

            val result = repository.save(board)

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
            val found = repository.findById(board.id)
            assertTrue(found.isLeft())
            assertIs<DomainError.BoardNotFound>(found.leftOrNull())
        }
}
