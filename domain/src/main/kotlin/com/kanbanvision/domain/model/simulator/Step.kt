package com.kanbanvision.domain.model.simulator

import java.time.Instant
import java.util.UUID

data class Step(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val requiredAbility: AbilityName,
    val createdDate: Instant = Instant.now(),
    val updatedDate: Instant = createdDate,
    val deletedDate: Instant? = null,
) {
    data class ExecutionResult(
        val updatedCard: SimulatorCard,
        val consumedEffort: Int,
        val isStepCompleted: Boolean,
    )

    init {
        require(id.isNotBlank()) { "Step id must not be blank" }
        require(name.isNotBlank()) { "Step name must not be blank" }
        require(!updatedDate.isBefore(createdDate)) { "Step updatedDate must be equal or after createdDate" }
        require(deletedDate == null || !deletedDate.isBefore(createdDate)) {
            "Step deletedDate must be equal or after createdDate when provided"
        }
    }

    fun canAssign(worker: Worker): Boolean = worker.hasAbility(requiredAbility)

    fun ensureCanAssign(worker: Worker) {
        require(canAssign(worker)) {
            "Worker '${worker.name}' cannot be assigned to step '$name' (required ability: $requiredAbility)"
        }
    }

    fun assignWorker(
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
