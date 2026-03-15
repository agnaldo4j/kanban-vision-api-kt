package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
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
class JdbcColumnRepositoryIntegrationTest {
    private val boardRepository = JdbcBoardRepository()
    private val repository = JdbcColumnRepository()

    private var existingBoardId: BoardId? = null

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    @BeforeEach
    fun cleanDatabase() =
        runBlocking {
            IntegrationTestSetup.cleanTables()
            val board =
                Board(
                    id = BoardId(UUID.randomUUID().toString()),
                    name = "Test Board",
                    createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),
                )
            boardRepository.save(board)
            existingBoardId = board.id
        }

    private fun newColumn(
        name: String = "Test Column",
        position: Int = 0,
    ) = Column(
        id = ColumnId(UUID.randomUUID().toString()),
        boardId = existingBoardId!!,
        name = name,
        position = position,
    )

    @Test
    fun `save persists column and findById returns it`() =
        runBlocking {
            val column = newColumn()

            repository.save(column)

            val found = repository.findById(column.id)
            assertNotNull(found)
            assertEquals(column.id, found.id)
            assertEquals(column.boardId, found.boardId)
            assertEquals(column.name, found.name)
            assertEquals(column.position, found.position)
        }

    @Test
    fun `findById returns null when column does not exist`() =
        runBlocking {
            val result = repository.findById(ColumnId(UUID.randomUUID().toString()))

            assertNull(result)
        }

    @Test
    fun `save updates existing column on conflict`() =
        runBlocking {
            val column = newColumn("Original", position = 0)
            repository.save(column)

            repository.save(column.copy(name = "Updated", position = 5))

            val found = repository.findById(column.id)
            assertEquals("Updated", found?.name)
            assertEquals(5, found?.position)
        }

    @Test
    fun `findByBoardId returns columns ordered by position`() =
        runBlocking {
            val col0 = newColumn("Backlog", position = 0)
            val col1 = newColumn("In Progress", position = 1)
            val col2 = newColumn("Done", position = 2)
            repository.save(col2)
            repository.save(col0)
            repository.save(col1)

            val found = repository.findByBoardId(existingBoardId!!)

            assertEquals(3, found.size)
            assertEquals(col0.id, found[0].id)
            assertEquals(col1.id, found[1].id)
            assertEquals(col2.id, found[2].id)
        }

    @Test
    fun `findByBoardId returns empty list when board has no columns`() =
        runBlocking {
            val found = repository.findByBoardId(existingBoardId!!)

            assertTrue(found.isEmpty())
        }

    @Test
    fun `delete removes column and returns true`() =
        runBlocking {
            val column = newColumn()
            repository.save(column)

            val deleted = repository.delete(column.id)

            assertTrue(deleted)
            assertNull(repository.findById(column.id))
        }

    @Test
    fun `delete returns false when column does not exist`() =
        runBlocking {
            val deleted = repository.delete(ColumnId(UUID.randomUUID().toString()))

            assertFalse(deleted)
        }

    @Test
    fun `save with non-existent boardId throws exception and no column is persisted`() {
        val orphanColumn =
            Column(
                id = ColumnId(UUID.randomUUID().toString()),
                boardId = BoardId(UUID.randomUUID().toString()),
                name = "Orphan",
                position = 0,
            )

        assertThrows<PSQLException> { runBlocking { repository.save(orphanColumn) } }

        val found = runBlocking { repository.findById(orphanColumn.id) }
        assertNull(found)
    }
}
