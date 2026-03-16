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

    @Test
    fun `save with name containing special characters persists correctly`() =
        runBlocking {
            val board = newBoard(name = "O'Reilly / Müller & \"Board\"")

            repository.save(board)

            val result = repository.findById(board.id)
            assertTrue(result.isRight())
            assertEquals("O'Reilly / Müller & \"Board\"", result.getOrNull()?.name)
        }

    @Test
    fun `findById with non-UUID string returns BoardNotFound`() =
        runBlocking<Unit> {
            val result = repository.findById(BoardId("not-a-valid-uuid"))

            assertTrue(result.isLeft())
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
        }
}
