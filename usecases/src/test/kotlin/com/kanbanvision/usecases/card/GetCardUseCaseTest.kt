package com.kanbanvision.usecases.card

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Card
import com.kanbanvision.usecases.card.queries.GetCardQuery
import com.kanbanvision.usecases.repositories.CardRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
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
            val card = Card.create(stepId = UUID.randomUUID().toString(), title = "My Task", position = 0)
            coEvery { cardRepository.findById(any()) } returns card.right()

            val result = useCase.execute(GetCardQuery(id = card.id))

            assertTrue(result.isRight())
            val returnedCard = result.getOrNull()
            assertNotNull(returnedCard)
            assertEquals(card.id, returnedCard.id)
            assertEquals(card.title, returnedCard.title)
        }

    @Test
    fun `execute returns CardNotFound when card not found`() =
        runTest {
            val id = UUID.randomUUID().toString()
            coEvery { cardRepository.findById(any()) } returns DomainError.CardNotFound(id).left()

            val result = useCase.execute(GetCardQuery(id = id))

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

    @Test
    fun `execute returns PersistenceError when repository returns error`() =
        runTest {
            coEvery { cardRepository.findById(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result = useCase.execute(GetCardQuery(id = UUID.randomUUID().toString()))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
