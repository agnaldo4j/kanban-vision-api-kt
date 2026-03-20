package com.kanbanvision.usecases.card

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Card
import com.kanbanvision.usecases.card.commands.MoveCardCommand
import com.kanbanvision.usecases.repositories.CardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MoveCardUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val useCase = MoveCardUseCase(cardRepository)

    @Test
    fun `execute moves card to target column at given position`() =
        runTest {
            val card = Card.create(columnId = UUID.randomUUID().toString(), title = "Task", position = 0)
            val targetColumnId = UUID.randomUUID().toString()
            var transformedCard: Card? = null
            coEvery { cardRepository.updateCard(card.id, any()) } answers {
                secondArg<(Card) -> Card>()(card).also { transformedCard = it }.right()
            }

            val result = useCase.execute(MoveCardCommand(cardId = card.id, targetColumnId = targetColumnId, newPosition = 2))

            assertTrue(result.isRight())
            assertEquals(targetColumnId, transformedCard?.columnId)
            assertEquals(2, transformedCard?.position)
        }

    @Test
    fun `execute returns CardNotFound when card not found`() =
        runTest {
            val id = UUID.randomUUID().toString()
            coEvery { cardRepository.updateCard(any(), any()) } returns DomainError.CardNotFound(id).left()

            val result =
                useCase.execute(
                    MoveCardCommand(
                        cardId = id,
                        targetColumnId = UUID.randomUUID().toString(),
                        newPosition = 0,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.CardNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute with blank card id returns ValidationError before querying repository`() =
        runTest {
            val result =
                useCase.execute(
                    MoveCardCommand(cardId = "", targetColumnId = UUID.randomUUID().toString(), newPosition = 0),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.updateCard(any(), any()) }
        }

    @Test
    fun `execute with blank target column id returns ValidationError before querying repository`() =
        runTest {
            val result =
                useCase.execute(
                    MoveCardCommand(cardId = UUID.randomUUID().toString(), targetColumnId = "", newPosition = 0),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.updateCard(any(), any()) }
        }

    @Test
    fun `execute with negative position returns ValidationError before querying repository`() =
        runTest {
            val result =
                useCase.execute(
                    MoveCardCommand(
                        cardId = UUID.randomUUID().toString(),
                        targetColumnId = UUID.randomUUID().toString(),
                        newPosition = -1,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.updateCard(any(), any()) }
        }

    @Test
    fun `execute returns PersistenceError when updateCard returns error`() =
        runTest {
            coEvery { cardRepository.updateCard(any(), any()) } returns DomainError.PersistenceError("DB failure").left()

            val result =
                useCase.execute(
                    MoveCardCommand(
                        cardId = UUID.randomUUID().toString(),
                        targetColumnId = UUID.randomUUID().toString(),
                        newPosition = 0,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
