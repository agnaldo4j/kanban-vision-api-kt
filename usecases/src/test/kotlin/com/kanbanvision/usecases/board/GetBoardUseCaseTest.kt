package com.kanbanvision.usecases.board

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.usecases.board.queries.GetBoardQuery
import com.kanbanvision.usecases.repositories.BoardRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GetBoardUseCaseTest {
    private val boardRepository = mockk<BoardRepository>()
    private val useCase = GetBoardUseCase(boardRepository)

    @Test
    fun `execute returns board when found`() =
        runTest {
            val board = Board.create("My Board")
            coEvery { boardRepository.findById(any()) } returns board

            val result = useCase.execute(GetBoardQuery(id = board.id.value))

            assertEquals(board.id, result.id)
            assertEquals(board.name, result.name)
        }

    @Test
    fun `execute throws when board not found`() =
        runTest {
            coEvery { boardRepository.findById(any()) } returns null

            assertFailsWith<NoSuchElementException> {
                useCase.execute(GetBoardQuery(id = BoardId.generate().value))
            }
        }

    @Test
    fun `execute with blank id throws before querying repository`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                useCase.execute(GetBoardQuery(id = ""))
            }
        }
}
