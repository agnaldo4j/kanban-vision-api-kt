package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.valueobjects.CardId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JdbcCardRepositoryEdgeCasesIntegrationTest : JdbcCardRepositoryTestBase() {
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
            assertEquals("Tarefa 'Revisão' & \"Deploy\"", result.getOrNull()?.title)
        }

    @Test
    fun `findById with non-UUID string returns CardNotFound`() =
        runBlocking<Unit> {
            val result = repository.findById(CardId("not-a-valid-uuid"))

            assertTrue(result.isLeft())
            assertIs<DomainError.CardNotFound>(result.leftOrNull())
        }
}
