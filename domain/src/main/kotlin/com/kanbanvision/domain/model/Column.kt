package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.team.Worker
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId

data class Column(
    val id: ColumnId,
    val boardId: BoardId,
    val name: String,
    val position: Int,
    val requiredAbility: AbilityName,
    val cards: List<Card> = emptyList(),
) {
    companion object {
        fun create(
            boardId: BoardId,
            name: String,
            position: Int,
            requiredAbility: AbilityName,
        ): Column {
            require(name.isNotBlank()) { "Column name must not be blank" }
            require(position >= 0) { "Column position must be non-negative" }
            return Column(
                id = ColumnId.generate(),
                boardId = boardId,
                name = name,
                position = position,
                requiredAbility = requiredAbility,
            )
        }
    }

    fun canAssign(worker: Worker): Boolean = worker.hasAbility(requiredAbility)

    fun ensureCanAssign(worker: Worker) {
        require(worker.hasAbility(requiredAbility)) {
            "Worker '${worker.name}' cannot be assigned to column '$name' (required ability: $requiredAbility)"
        }
    }

    fun assignWorker(
        worker: Worker,
        currentAssignments: Map<Worker, ColumnId>,
    ): Map<Worker, ColumnId> {
        ensureCanAssign(worker)
        val assignedColumnId = currentAssignments[worker]
        require(assignedColumnId == null || assignedColumnId == id) {
            "Worker '${worker.name}' is already assigned to another column (${assignedColumnId?.value})"
        }
        return currentAssignments + (worker to id)
    }
}
