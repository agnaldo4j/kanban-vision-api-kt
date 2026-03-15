package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
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
class JdbcCardRepositoryIntegrationTest {
    private val boardRepository = JdbcBoardRepository()
    private val columnRepository = JdbcColumnRepository()
    private val repository = JdbcCardRepository()

    private var existingColumnId: ColumnId? = null

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
            val column =
                Column(
                    id = ColumnId(UUID.randomUUID().toString()),
                    boardId = board.id,
                    name = "Test Column",
                    position = 0,
                )
            columnRepository.save(column)
            existingColumnId = column.id
        }

    private fun newCard(
        title: String = "Test Card",
        position: Int = 0,
    ) = Card(
        id = CardId(UUID.randomUUID().toString()),
        columnId = existingColumnId!!,
        title = title,
        description = "Description",
        position = position,
        createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),
    )

    @Test
    fun `save persists card and findById returns it`() =
        runBlocking {
            val card = newCard()

            repository.save(card)

            val result = repository.findById(card.id)
            assertTrue(result.isRight())
            val found = result.getOrNull()
            assertNotNull(found)
            assertEquals(card.id, found.id)
            assertEquals(card.columnId, found.columnId)
            assertEquals(card.title, found.title)
            assertEquals(card.description, found.description)
            assertEquals(card.position, found.position)
            assertEquals(card.createdAt, found.createdAt)
        }

    @Test
    fun `findById returns CardNotFound when card does not exist`() =
        runBlocking<Unit> {
            val result = repository.findById(CardId(UUID.randomUUID().toString()))

            assertTrue(result.isLeft())
            assertIs<DomainError.CardNotFound>(result.leftOrNull())
        }

    @Test
    fun `save updates existing card on conflict`() =
        runBlocking {
            val card = newCard("Original Title")
            repository.save(card)

            repository.save(card.copy(title = "Updated Title", position = 99))

            val result = repository.findById(card.id)
            assertEquals("Updated Title", result.getOrNull()?.title)
            assertEquals(99, result.getOrNull()?.position)
        }

    @Test
    fun `findByColumnId returns cards ordered by position`() =
        runBlocking {
            val card0 = newCard("First", position = 0)
            val card1 = newCard("Second", position = 1)
            val card2 = newCard("Third", position = 2)
            repository.save(card2)
            repository.save(card0)
            repository.save(card1)

            val result = repository.findByColumnId(existingColumnId!!)

            assertTrue(result.isRight())
            val found = result.getOrNull()!!
            assertEquals(3, found.size)
            assertEquals(card0.id, found[0].id)
            assertEquals(card1.id, found[1].id)
            assertEquals(card2.id, found[2].id)
        }

    @Test
    fun `findByColumnId returns empty list when column has no cards`() =
        runBlocking {
            val result = repository.findByColumnId(existingColumnId!!)

            assertTrue(result.isRight())
            assertTrue(result.getOrNull()!!.isEmpty())
        }

    @Test
    fun `save with non-existent columnId returns PersistenceError and no card is persisted`() =
        runBlocking<Unit> {
            val orphanCard =
                Card(
                    id = CardId(UUID.randomUUID().toString()),
                    columnId = ColumnId(UUID.randomUUID().toString()),
                    title = "Orphan",
                    position = 0,
                    createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),
                )

            val result = repository.save(orphanCard)

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
            val found = repository.findById(orphanCard.id)
            assertTrue(found.isLeft())
            assertIs<DomainError.CardNotFound>(found.leftOrNull())
        }

    @Test
    fun `updateCard applies transform and persists result atomically`() =
        runBlocking {
            val card = newCard("Original", position = 0)
            repository.save(card)

            val result = repository.updateCard(card.id) { it.moveTo(existingColumnId!!, newPosition = 5) }

            assertTrue(result.isRight())
            val updated = result.getOrNull()
            assertNotNull(updated)
            assertEquals(card.id, updated.id)
            assertEquals(existingColumnId, updated.columnId)
            assertEquals(5, updated.position)
            val found = repository.findById(card.id)
            assertEquals(5, found.getOrNull()?.position)
        }

    @Test
    fun `updateCard returns CardNotFound when card does not exist`() =
        runBlocking<Unit> {
            val result = repository.updateCard(CardId(UUID.randomUUID().toString())) { it }

            assertTrue(result.isLeft())
            assertIs<DomainError.CardNotFound>(result.leftOrNull())
        }

    @Test
    fun `updateCard returns PersistenceError and does not modify card when update violates constraint`() =
        runBlocking<Unit> {
            val card = newCard("Atomic Test", position = 0)
            repository.save(card)
            val nonExistentColumnId = ColumnId(UUID.randomUUID().toString())

            val result = repository.updateCard(card.id) { it.copy(columnId = nonExistentColumnId) }

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
            val found = repository.findById(card.id)
            assertTrue(found.isRight())
            assertEquals(existingColumnId, found.getOrNull()?.columnId)
        }
}
