package com.kanbanvision.usecases.card

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.commands.MoveCardCommand
import com.kanbanvision.usecases.repositories.CardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MoveCardUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val useCase = MoveCardUseCase(cardRepository)

    @Test
    fun `execute moves card to target column at given position`() =
        runTest {
            val card = Card.create(columnId = ColumnId.generate(), title = "Task", position = 0)
            val targetColumnId = ColumnId.generate().value
            coEvery { cardRepository.findById(CardId(card.id.value)) } returns card.right()
            coEvery { cardRepository.save(any()) } answers { firstArg<Card>().right() }

            val result = useCase.execute(MoveCardCommand(cardId = card.id.value, targetColumnId = targetColumnId, newPosition = 2))

            assertTrue(result.isRight())
            coVerify {
                cardRepository.save(
                    match { it.columnId.value == targetColumnId && it.position == 2 },
                )
            }
        }

    @Test
    fun `execute returns CardNotFound when card not found`() =
        runTest {
            val id = CardId.generate().value
            coEvery { cardRepository.findById(any()) } returns DomainError.CardNotFound(id).left()

            val result =
                useCase.execute(
                    MoveCardCommand(
                        cardId = id,
                        targetColumnId = ColumnId.generate().value,
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
                    MoveCardCommand(cardId = "", targetColumnId = ColumnId.generate().value, newPosition = 0),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.findById(any()) }
        }

    @Test
    fun `execute with blank target column id returns ValidationError before querying repository`() =
        runTest {
            val result =
                useCase.execute(
                    MoveCardCommand(cardId = CardId.generate().value, targetColumnId = "", newPosition = 0),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.findById(any()) }
        }

    @Test
    fun `execute with negative position returns ValidationError before querying repository`() =
        runTest {
            val result =
                useCase.execute(
                    MoveCardCommand(
                        cardId = CardId.generate().value,
                        targetColumnId = ColumnId.generate().value,
                        newPosition = -1,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.findById(any()) }
        }

    @Test
    fun `execute returns PersistenceError when find returns error`() =
        runTest {
            coEvery { cardRepository.findById(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result =
                useCase.execute(
                    MoveCardCommand(
                        cardId = CardId.generate().value,
                        targetColumnId = ColumnId.generate().value,
                        newPosition = 0,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `execute returns PersistenceError when save returns error`() =
        runTest {
            val card = Card.create(columnId = ColumnId.generate(), title = "Task", position = 0)
            coEvery { cardRepository.findById(any()) } returns card.right()
            coEvery { cardRepository.save(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result =
                useCase.execute(
                    MoveCardCommand(
                        cardId = card.id.value,
                        targetColumnId = ColumnId.generate().value,
                        newPosition = 0,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
