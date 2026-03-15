package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

            val found = repository.findById(board.id)
            assertNotNull(found)
            assertEquals(board.id, found.id)
            assertEquals(board.name, found.name)
            assertEquals(board.createdAt, found.createdAt)
        }

    @Test
    fun `findById returns null when board does not exist`() =
        runBlocking {
            val result = repository.findById(BoardId(UUID.randomUUID().toString()))

            assertNull(result)
        }

    @Test
    fun `save updates existing board name on conflict`() =
        runBlocking {
            val board = newBoard("Original Name")
            repository.save(board)

            repository.save(board.copy(name = "Updated Name"))

            val found = repository.findById(board.id)
            assertEquals("Updated Name", found?.name)
        }

    @Test
    fun `findAll returns all saved boards`() =
        runBlocking {
            repository.save(newBoard("Board One"))
            repository.save(newBoard("Board Two"))

            val all = repository.findAll()

            assertEquals(2, all.size)
        }

    @Test
    fun `findAll returns empty list when no boards exist`() =
        runBlocking {
            val all = repository.findAll()

            assertTrue(all.isEmpty())
        }

    @Test
    fun `delete removes board and returns true`() =
        runBlocking {
            val board = newBoard()
            repository.save(board)

            val deleted = repository.delete(board.id)

            assertTrue(deleted)
            assertNull(repository.findById(board.id))
        }

    @Test
    fun `delete returns false when board does not exist`() =
        runBlocking {
            val deleted = repository.delete(BoardId(UUID.randomUUID().toString()))

            assertFalse(deleted)
        }

    @Test
    fun `save with name exceeding column limit throws exception and no board is persisted`() {
        val board =
            Board(
                id = BoardId(UUID.randomUUID().toString()),
                name = "x".repeat(256),
                createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),
            )

        assertThrows<PSQLException> { runBlocking { repository.save(board) } }

        val found = runBlocking { repository.findById(board.id) }
        assertNull(found)
    }
}
