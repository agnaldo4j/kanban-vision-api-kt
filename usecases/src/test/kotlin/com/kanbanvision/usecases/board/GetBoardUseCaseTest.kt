package com.kanbanvision.usecases.board

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.usecases.board.queries.GetBoardQuery
import com.kanbanvision.usecases.repositories.BoardRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetBoardUseCaseTest {
    private val boardRepository = mockk<BoardRepository>()
    private val useCase = GetBoardUseCase(boardRepository)

    @Test
    fun `execute returns board when found`() =
        runTest {
            val board = Board.create("My Board")
            coEvery { boardRepository.findById(any()) } returns board.right()

            val result = useCase.execute(GetBoardQuery(id = board.id))

            assertTrue(result.isRight())
            val returnedBoard = result.getOrNull()
            assertNotNull(returnedBoard)
            assertEquals(board.id, returnedBoard.id)
            assertEquals(board.name, returnedBoard.name)
        }

    @Test
    fun `execute returns BoardNotFound when board not found`() =
        runTest {
            val id = UUID.randomUUID().toString()
            coEvery { boardRepository.findById(any()) } returns DomainError.BoardNotFound(id).left()

            val result = useCase.execute(GetBoardQuery(id = id))

            assertTrue(result.isLeft())
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute with blank id returns ValidationError`() =
        runTest {
            val result = useCase.execute(GetBoardQuery(id = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }

    @Test
    fun `execute returns PersistenceError when repository returns error`() =
        runTest {
            coEvery { boardRepository.findById(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result = useCase.execute(GetBoardQuery(id = UUID.randomUUID().toString()))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
