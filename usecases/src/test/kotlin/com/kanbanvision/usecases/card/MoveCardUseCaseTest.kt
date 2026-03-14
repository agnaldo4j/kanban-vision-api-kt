package com.kanbanvision.usecases.card

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
import kotlin.test.assertFailsWith

class MoveCardUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val useCase = MoveCardUseCase(cardRepository)

    @Test
    fun `execute moves card to target column at given position`() =
        runTest {
            val card = Card.create(columnId = ColumnId.generate(), title = "Task", position = 0)
            val targetColumnId = ColumnId.generate().value
            coEvery { cardRepository.findById(CardId(card.id.value)) } returns card
            coEvery { cardRepository.save(any()) } answers { firstArg() }

            useCase.execute(MoveCardCommand(cardId = card.id.value, targetColumnId = targetColumnId, newPosition = 2))

            coVerify {
                cardRepository.save(
                    match { it.columnId.value == targetColumnId && it.position == 2 },
                )
            }
        }

    @Test
    fun `execute throws when card not found`() =
        runTest {
            coEvery { cardRepository.findById(any()) } returns null

            assertFailsWith<NoSuchElementException> {
                useCase.execute(
                    MoveCardCommand(
                        cardId = CardId.generate().value,
                        targetColumnId = ColumnId.generate().value,
                        newPosition = 0,
                    ),
                )
            }
        }

    @Test
    fun `execute with blank card id throws before querying repository`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                useCase.execute(
                    MoveCardCommand(cardId = "", targetColumnId = ColumnId.generate().value, newPosition = 0),
                )
            }
            coVerify(exactly = 0) { cardRepository.findById(any()) }
        }

    @Test
    fun `execute with blank target column id throws before querying repository`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                useCase.execute(
                    MoveCardCommand(cardId = CardId.generate().value, targetColumnId = "", newPosition = 0),
                )
            }
            coVerify(exactly = 0) { cardRepository.findById(any()) }
        }

    @Test
    fun `execute with negative position throws before querying repository`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                useCase.execute(
                    MoveCardCommand(
                        cardId = CardId.generate().value,
                        targetColumnId = ColumnId.generate().value,
                        newPosition = -1,
                    ),
                )
            }
            coVerify(exactly = 0) { cardRepository.findById(any()) }
        }
}
