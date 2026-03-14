package com.kanbanvision.usecases.board

import com.kanbanvision.usecases.board.commands.CreateBoardCommand
import com.kanbanvision.usecases.repositories.BoardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CreateBoardUseCaseTest {
    private val boardRepository = mockk<BoardRepository>()
    private val useCase = CreateBoardUseCase(boardRepository)

    @Test
    fun `execute saves board and returns its id`() =
        runTest {
            coEvery { boardRepository.save(any()) } answers { firstArg() }

            val command = CreateBoardCommand(name = "Sprint Board")
            val boardId = useCase.execute(command)

            assertNotNull(boardId)
            coVerify(exactly = 1) { boardRepository.save(any()) }
        }

    @Test
    fun `execute with blank name throws before saving`() =
        runTest {
            val command = CreateBoardCommand(name = "")

            assertFailsWith<IllegalArgumentException> { useCase.execute(command) }

            coVerify(exactly = 0) { boardRepository.save(any()) }
        }

    @Test
    fun `execute with whitespace-only name throws before saving`() =
        runTest {
            val command = CreateBoardCommand(name = "   ")

            assertFailsWith<IllegalArgumentException> { useCase.execute(command) }

            coVerify(exactly = 0) { boardRepository.save(any()) }
        }
}
