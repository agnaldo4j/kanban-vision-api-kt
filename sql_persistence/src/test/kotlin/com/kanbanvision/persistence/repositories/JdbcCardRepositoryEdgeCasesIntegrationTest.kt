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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcCardRepositoryEdgeCasesIntegrationTest {
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

    private fun newCard(title: String = "Test Card") =
        Card(
            id = CardId(UUID.randomUUID().toString()),
            columnId = existingColumnId!!,
            title = title,
            description = "Description",
            position = 0,
            createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),
        )

    @Test
    fun `save with title exceeding column limit returns PersistenceError`() =
        runBlocking<Unit> {
            val card = newCard(title = "x".repeat(256))

            val result = repository.save(card)

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `save with title containing special characters persists correctly`() =
        runBlocking {
            val card = newCard(title = "Tarefa 'Revisão' & \"Deploy\"")

            repository.save(card)

            val result = repository.findById(card.id)
            assertTrue(result.isRight())
            kotlin.test.assertEquals("Tarefa 'Revisão' & \"Deploy\"", result.getOrNull()?.title)
        }

    @Test
    fun `findById with non-UUID string returns CardNotFound`() =
        runBlocking<Unit> {
            val result = repository.findById(CardId("not-a-valid-uuid"))

            assertTrue(result.isLeft())
            assertIs<DomainError.CardNotFound>(result.leftOrNull())
        }
}
