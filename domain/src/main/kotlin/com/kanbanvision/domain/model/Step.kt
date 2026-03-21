package com.kanbanvision.domain.model

import java.util.UUID

data class Step(
    override val id: String = UUID.randomUUID().toString(),
    val board: BoardRef,
    val name: String,
    val position: Int = 0,
    val requiredAbility: AbilityName,
    val cards: List<Card> = emptyList(),
    val workers: List<Worker> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain {
    data class ExecutionResult(
        val updatedCard: Card,
        val consumedEffort: Int,
        val isStepCompleted: Boolean,
    )

    init {
        require(id.isNotBlank()) { "Step id must not be blank" }
        require(name.isNotBlank()) { "Step name must not be blank" }
        require(position >= 0) { "Step position must be non-negative" }
        require(workers.all { it.hasAbility(requiredAbility) }) {
            "All workers assigned to step '$name' must have required ability $requiredAbility"
        }
    }

    val boardId: String
        get() = board.id

    constructor(
        id: String = UUID.randomUUID().toString(),
        boardId: String,
        name: String,
        position: Int = 0,
        requiredAbility: AbilityName,
        cards: List<Card> = emptyList(),
        workers: List<Worker> = emptyList(),
        audit: Audit = Audit(),
    ) : this(
        id = id,
        board = BoardRef(boardId),
        name = name,
        position = position,
        requiredAbility = requiredAbility,
        cards = cards,
        workers = workers,
        audit = audit,
    )

    companion object {
        fun create(
            board: BoardRef,
            name: String,
            position: Int,
            requiredAbility: AbilityName,
        ): Step {
            require(name.isNotBlank()) { "Step name must not be blank" }
            require(position >= 0) { "Step position must be non-negative" }
            return Step(
                id = UUID.randomUUID().toString(),
                board = board,
                name = name,
                position = position,
                requiredAbility = requiredAbility,
            )
        }

        fun create(
            boardId: String,
            name: String,
            position: Int,
            requiredAbility: AbilityName,
        ): Step = create(board = BoardRef(boardId), name = name, position = position, requiredAbility = requiredAbility)
    }

    fun canAssign(worker: Worker): Boolean = worker.hasAbility(requiredAbility)

    fun assignWorker(worker: Worker): Step {
        require(canAssign(worker)) {
            "Worker '${worker.name}' cannot be assigned to step '$name' (required ability: $requiredAbility)"
        }
        require(workers.none { it.id == worker.id }) { "Worker '${worker.name}' is already assigned to step '$name'" }
        return copy(workers = workers + worker)
    }

    fun unassignWorker(workerId: String): Step = copy(workers = workers.filterNot { it.id == workerId })

    fun executeCard(
        worker: Worker,
        card: Card,
        dailyCapacities: Map<AbilityName, Int>,
    ): ExecutionResult {
        require(canAssign(worker)) {
            "Worker '${worker.name}' cannot execute step '$name' (required ability: $requiredAbility)"
        }

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
