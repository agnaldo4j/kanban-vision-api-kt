package com.kanbanvision.domain.model.kanban

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import java.time.Instant
import java.util.UUID

data class Step(
    override val id: StepId = StepId(UUID.randomUUID().toString()),
    val board: BoardId,
    val name: String,
    val position: Int = 0,
    val requiredAbility: AbilityName,
    val cards: List<Card> = emptyList(),
    val workers: List<Worker> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain<StepId> {
    data class ExecutionResult(
        val updatedCard: Card,
        val consumedEffort: Int,
        val isStepCompleted: Boolean,
    )

    init {
        require(name.isNotBlank()) { "Step name must not be blank" }
        require(position >= 0) { "Step position must be non-negative" }
        require(workers.all { it.hasAbility(requiredAbility) }) {
            "All workers assigned to step '$name' must have required ability $requiredAbility"
        }
    }

    companion object {
        fun create(
            board: BoardId,
            name: String,
            position: Int,
            requiredAbility: AbilityName,
        ): Step {
            require(name.isNotBlank()) { "Step name must not be blank" }
            require(position >= 0) { "Step position must be non-negative" }
            return Step(
                id = StepId(UUID.randomUUID().toString()),
                board = board,
                name = name,
                position = position,
                requiredAbility = requiredAbility,
            )
        }
    }

    fun canAssign(worker: Worker): Boolean = worker.hasAbility(requiredAbility)

    // ADR-0044: regras de domínio (elegibilidade/duplicidade de worker) → Either.
    fun assignWorker(worker: Worker): Either<KanbanError, Step> =
        either {
            ensure(canAssign(worker)) { KanbanError.WorkerCannotExecuteStep(worker.id, id.value) }
            ensure(workers.none { it.id == worker.id }) { KanbanError.WorkerAlreadyAssigned(worker.id) }
            copy(workers = workers + worker)
        }

    fun toRef(): StepId = id

    fun unassignWorker(workerId: String): Step = copy(workers = workers.filterNot { it.id == workerId })

    fun executeCard(
        worker: Worker,
        card: Card,
        dailyCapacities: Map<AbilityName, Int>,
        now: Instant,
    ): Either<KanbanError, ExecutionResult> =
        either {
            ensure(canAssign(worker)) { KanbanError.WorkerCannotExecuteStep(worker.id, id.value) }

            val remaining = card.remainingEffortFor(requiredAbility)
            if (remaining == 0) {
                return@either ExecutionResult(updatedCard = card, consumedEffort = 0, isStepCompleted = true)
            }

            val available = dailyCapacities[requiredAbility] ?: 0
            val consumed =
                when (requiredAbility) {
                    // DEPLOYER consome tudo que resta (deploy é atômico); as demais habilidades
                    // consomem só o que a capacidade diária do worker permite.
                    AbilityName.DEPLOYER -> remaining
                    AbilityName.PRODUCT_MANAGER,
                    AbilityName.DEVELOPER,
                    AbilityName.TESTER,
                    -> minOf(remaining, available.coerceAtLeast(0))
                }

            val updated = card.consumeEffort(requiredAbility, consumed, now)
            ExecutionResult(
                updatedCard = updated,
                consumedEffort = consumed,
                isStepCompleted = updated.remainingEffortFor(requiredAbility) == 0,
            )
        }
}
