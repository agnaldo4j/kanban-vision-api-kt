package com.kanbanvision.usecases.board

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.port.BoardRepository

class CreateBoardUseCase(
    private val boardRepository: BoardRepository,
) {
    suspend fun execute(name: String): Board {
        val board = Board.create(name)
        return boardRepository.save(board)
    }
}
