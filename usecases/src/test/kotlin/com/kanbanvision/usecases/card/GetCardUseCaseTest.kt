package com.kanbanvision.usecases.card

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.queries.GetCardQuery
import com.kanbanvision.usecases.repositories.CardRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GetCardUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val useCase = GetCardUseCase(cardRepository)

    @Test
    fun `execute returns card when found`() =
        runTest {
            val card = Card.create(columnId = ColumnId.generate(), title = "My Task", position = 0)
            coEvery { cardRepository.findById(any()) } returns card

            val result = useCase.execute(GetCardQuery(id = card.id.value))

            assertEquals(card.id, result.id)
            assertEquals(card.title, result.title)
        }

    @Test
    fun `execute throws when card not found`() =
        runTest {
            coEvery { cardRepository.findById(any()) } returns null

            assertFailsWith<NoSuchElementException> {
                useCase.execute(GetCardQuery(id = CardId.generate().value))
            }
        }

    @Test
    fun `execute with blank id throws before querying repository`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                useCase.execute(GetCardQuery(id = ""))
            }
        }
}
