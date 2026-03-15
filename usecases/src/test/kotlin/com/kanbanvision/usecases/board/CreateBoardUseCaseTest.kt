package com.kanbanvision.usecases.board

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.usecases.board.commands.CreateBoardCommand
import com.kanbanvision.usecases.repositories.BoardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateBoardUseCaseTest {
    private val boardRepository = mockk<BoardRepository>()
    private val useCase = CreateBoardUseCase(boardRepository)

    @Test
    fun `execute saves board and returns its id`() =
        runTest {
            coEvery { boardRepository.save(any()) } answers { firstArg<Board>().right() }

            val command = CreateBoardCommand(name = "Sprint Board")
            val result = useCase.execute(command)

            assertTrue(result.isRight())
            assertNotNull(result.getOrNull())
            coVerify(exactly = 1) { boardRepository.save(any()) }
        }

    @Test
    fun `execute with blank name returns ValidationError before saving`() =
        runTest {
            val command = CreateBoardCommand(name = "")

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { boardRepository.save(any()) }
        }

    @Test
    fun `execute with whitespace-only name returns ValidationError before saving`() =
        runTest {
            val command = CreateBoardCommand(name = "   ")

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { boardRepository.save(any()) }
        }

    @Test
    fun `execute returns PersistenceError when repository returns error`() =
        runTest {
            coEvery { boardRepository.save(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result = useCase.execute(CreateBoardCommand(name = "Board"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
