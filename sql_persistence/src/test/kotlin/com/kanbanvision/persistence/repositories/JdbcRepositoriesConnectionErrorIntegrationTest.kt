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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcRepositoriesConnectionErrorIntegrationTest {
    private val boardRepository = JdbcBoardRepository()
    private val columnRepository = JdbcColumnRepository()
    private val cardRepository = JdbcCardRepository()

    @BeforeAll
    fun setup() {
        IntegrationTestSetup.ensureInitialized()
        IntegrationTestSetup.closeDataSource()
    }

    @AfterAll
    fun teardown() {
        IntegrationTestSetup.reinitDataSource()
    }

    @Test
    fun `board save returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val board =
                Board(
                    id = BoardId(UUID.randomUUID().toString()),
                    name = "Board",
                    createdAt = Instant.now(),
                )
            val result = boardRepository.save(board)
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `board findById returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = boardRepository.findById(BoardId(UUID.randomUUID().toString()))
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `board findAll returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = boardRepository.findAll()
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `board delete returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = boardRepository.delete(BoardId(UUID.randomUUID().toString()))
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `column save returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val column =
                Column(
                    id = ColumnId(UUID.randomUUID().toString()),
                    boardId = BoardId(UUID.randomUUID().toString()),
                    name = "Column",
                    position = 0,
                )
            val result = columnRepository.save(column)
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `column findById returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = columnRepository.findById(ColumnId(UUID.randomUUID().toString()))
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `column findByBoardId returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = columnRepository.findByBoardId(BoardId(UUID.randomUUID().toString()))
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `column delete returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = columnRepository.delete(ColumnId(UUID.randomUUID().toString()))
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `card save returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val card =
                Card(
                    id = CardId(UUID.randomUUID().toString()),
                    columnId = ColumnId(UUID.randomUUID().toString()),
                    title = "Card",
                    description = "",
                    position = 0,
                    createdAt = Instant.now(),
                )
            val result = cardRepository.save(card)
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `card findById returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = cardRepository.findById(CardId(UUID.randomUUID().toString()))
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `card findByColumnId returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = cardRepository.findByColumnId(ColumnId(UUID.randomUUID().toString()))
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `card delete returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = cardRepository.delete(CardId(UUID.randomUUID().toString()))
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
