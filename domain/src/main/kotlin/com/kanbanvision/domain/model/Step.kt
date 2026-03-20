package com.kanbanvision.domain.model

import java.util.UUID

data class Step(
    val id: String = UUID.randomUUID().toString(),
    val boardId: String = UUID.randomUUID().toString(),
    val name: String,
    val position: Int = 0,
    val requiredAbility: AbilityName,
    val cards: List<Card> = emptyList(),
    val audit: Audit = Audit(),
) {
    data class ExecutionResult(
        val updatedCard: SimulatorCard,
        val consumedEffort: Int,
        val isStepCompleted: Boolean,
    )

    val createdDate get() = audit.createdAt
    val updatedDate get() = audit.updatedAt
    val deletedDate get() = audit.deletedAt

    init {
        require(id.isNotBlank()) { "Step id must not be blank" }
        require(boardId.isNotBlank()) { "Step boardId must not be blank" }
        require(name.isNotBlank()) { "Step name must not be blank" }
        require(position >= 0) { "Step position must be non-negative" }
    }

    companion object {
        fun create(
            boardId: String,
            name: String,
            position: Int,
            requiredAbility: AbilityName,
        ): Step {
            require(name.isNotBlank()) { "Step name must not be blank" }
            require(position >= 0) { "Step position must be non-negative" }
            return Step(
                id = UUID.randomUUID().toString(),
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
        currentAssignments: Map<Worker, String>,
    ): Map<Worker, String> {
        ensureCanAssign(worker)
        val assignedStepId = currentAssignments[worker]
        require(assignedStepId == null || assignedStepId == id) {
            "Worker '${worker.name}' is already assigned to another step ($assignedStepId)"
        }
        return currentAssignments + (worker to id)
    }

    fun assignWorkerByWorkerId(
        worker: Worker,
        currentAssignments: Map<String, String>,
    ): Map<String, String> {
        ensureCanAssign(worker)
        val assignedStepId = currentAssignments[worker.id]
        require(assignedStepId == null || assignedStepId == id) {
            "Worker '${worker.name}' is already assigned to another step ($assignedStepId)"
        }
        return currentAssignments + (worker.id to id)
    }

    fun executeCard(
        worker: Worker,
        card: SimulatorCard,
        dailyCapacities: Map<AbilityName, Int>,
    ): ExecutionResult {
        ensureCanAssign(worker)
        val remaining = card.remainingEffortFor(requiredAbility)
        if (remaining == 0) {
            return ExecutionResult(updatedCard = card, consumedEffort = 0, isStepCompleted = true)
        }

        val available = dailyCapacities[requiredAbility] ?: 0
        val consumed =
            when (requiredAbility) {
                AbilityName.DEPLOYER -> remaining
                else -> minOf(remaining, available.coerceAtLeast(0))
            }

        val updated = card.consumeEffort(requiredAbility, consumed)
        return ExecutionResult(
            updatedCard = updated,
            consumedEffort = consumed,
            isStepCompleted = updated.remainingEffortFor(requiredAbility) == 0,
        )
    }
}
