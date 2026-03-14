package com.kanbanvision.usecases.board

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.port.BoardRepository

class GetBoardUseCase(
    private val boardRepository: BoardRepository,
) {
    suspend fun execute(id: BoardId): Board =
        boardRepository.findById(id) ?: throw NoSuchElementException("Board '${id.value}' not found")
}
