package com.kanbanvision.usecases.card

import com.kanbanvision.domain.errors.DomainError
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetCardUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val useCase = GetCardUseCase(cardRepository)

    @Test
    fun `execute returns card when found`() =
        runTest {
            val card = Card.create(columnId = ColumnId.generate(), title = "My Task", position = 0)
            coEvery { cardRepository.findById(any()) } returns card

            val result = useCase.execute(GetCardQuery(id = card.id.value))

            assertTrue(result.isRight())
            val returnedCard = result.getOrNull()
            assertNotNull(returnedCard)
            assertEquals(card.id, returnedCard.id)
            assertEquals(card.title, returnedCard.title)
        }

    @Test
    fun `execute returns CardNotFound when card not found`() =
        runTest {
            coEvery { cardRepository.findById(any()) } returns null

            val result = useCase.execute(GetCardQuery(id = CardId.generate().value))

            assertTrue(result.isLeft())
            assertIs<DomainError.CardNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute with blank id returns ValidationError`() =
        runTest {
            val result = useCase.execute(GetCardQuery(id = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }
}
