package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.team.Worker
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId

data class Step(
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
        ): Step {
            require(name.isNotBlank()) { "Step name must not be blank" }
            require(position >= 0) { "Step position must be non-negative" }
            return Step(
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
            "Worker '${worker.name}' cannot be assigned to step '$name' (required ability: $requiredAbility)"
        }
    }

    fun assignWorker(
        worker: Worker,
        currentAssignments: Map<Worker, ColumnId>,
    ): Map<Worker, ColumnId> {
        ensureCanAssign(worker)
        val assignedStepId = currentAssignments[worker]
        require(assignedStepId == null || assignedStepId == id) {
            "Worker '${worker.name}' is already assigned to another step (${assignedStepId?.value})"
        }
        return currentAssignments + (worker to id)
    }
}
