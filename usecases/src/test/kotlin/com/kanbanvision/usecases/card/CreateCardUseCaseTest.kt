package com.kanbanvision.usecases.card

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.repositories.CardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateCardUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val useCase = CreateCardUseCase(cardRepository)

    private val columnId = ColumnId.generate().value

    @Test
    fun `execute saves card and returns its id when column is empty`() =
        runTest {
            coEvery { cardRepository.findByColumnId(any()) } returns emptyList()
            coEvery { cardRepository.save(any()) } answers { firstArg() }

            val result = useCase.execute(CreateCardCommand(columnId = columnId, title = "Fix bug"))

            assertTrue(result.isRight())
            assertNotNull(result.getOrNull())
            coVerify(exactly = 1) { cardRepository.save(any()) }
        }

    @Test
    fun `execute assigns position equal to existing cards count`() =
        runTest {
            val existingCard = Card.create(columnId = ColumnId(columnId), title = "Existing", position = 0)
            coEvery { cardRepository.findByColumnId(any()) } returns listOf(existingCard)
            coEvery { cardRepository.save(any()) } answers { firstArg() }

            useCase.execute(CreateCardCommand(columnId = columnId, title = "New Card"))

            coVerify { cardRepository.save(match { it.position == 1 }) }
        }

    @Test
    fun `execute with blank column id returns ValidationError before saving`() =
        runTest {
            val result = useCase.execute(CreateCardCommand(columnId = "", title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.save(any()) }
        }

    @Test
    fun `execute with blank title returns ValidationError before saving`() =
        runTest {
            val result = useCase.execute(CreateCardCommand(columnId = columnId, title = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.save(any()) }
        }

    @Test
    fun `execute returns PersistenceError when repository throws`() =
        runTest {
            coEvery { cardRepository.findByColumnId(any()) } throws RuntimeException("DB failure")

            val result = useCase.execute(CreateCardCommand(columnId = columnId, title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
