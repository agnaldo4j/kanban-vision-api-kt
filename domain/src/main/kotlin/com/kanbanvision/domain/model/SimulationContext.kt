package com.kanbanvision.domain.model

data class SimulationContext(
    val organizationId: String,
    val boardId: String,
    val steps: List<Step> = emptyList(),
    val tribes: List<Tribe> = emptyList(),
    val workerAssignments: Map<String, String> = emptyMap(),
    val audit: Audit = Audit(),
) {
    init {
        require(organizationId.isNotBlank()) { "SimulationContext organizationId must not be blank" }
        require(boardId.isNotBlank()) { "SimulationContext boardId must not be blank" }
    }

    val workers: List<Worker>
        get() = tribes.flatMap { tribe -> tribe.squads.flatMap { squad -> squad.workers } }

    fun findStep(stepId: String): Step? = steps.firstOrNull { it.id == stepId }

    fun findWorker(workerId: String): Worker? = workers.firstOrNull { it.id == workerId }

    fun canAssign(
        worker: Worker,
        step: Step,
    ): Boolean =
        worker.hasAbility(step.requiredAbility) &&
            workerAssignments[worker.id].let { assignedStepId -> assignedStepId == null || assignedStepId == step.id }

    fun assign(
        worker: Worker,
        step: Step,
    ): SimulationContext {
        require(step.boardId == boardId) { "Step ${step.id} does not belong to board $boardId" }
        require(worker.hasAbility(step.requiredAbility)) {
            "Worker '${worker.name}' cannot be assigned to step '${step.name}' (required ability: ${step.requiredAbility})"
        }
        val assignedStepId = workerAssignments[worker.id]
        require(assignedStepId == null || assignedStepId == step.id) {
            "Worker '${worker.name}' is already assigned to another step ($assignedStepId)"
        }
        return copy(workerAssignments = workerAssignments + (worker.id to step.id))
    }
}
