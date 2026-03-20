package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.DecisionType
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.PolicySet
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.SimulationContext
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationResult
import com.kanbanvision.domain.model.SimulationState
import kotlin.random.Random

object SimulationEngine {
    /**
     * Runs a single simulation day. Pure function: same inputs always produce the same output.
     * [seed] controls ordering of non-EXPEDITE TODO items during auto-advance;
     * EXPEDITE items are always prioritized first in their original list order.
     */
    fun runDay(
        scenarioId: String,
        state: SimulationState,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult {
        val ctx = EngineContext(day = state.currentDay.value, movements = mutableListOf(), rng = Random(seed))

        val afterDecisions = applyDecisions(state.cards, decisions, ctx)
        val afterAutoAdvance = autoAdvance(afterDecisions, state.policySet, ctx)
        val afterExecution = applyAssignedWorkerExecution(afterAutoAdvance, state.context, ctx)
        val afterAging =
            afterExecution.map { item ->
                if (item.state != CardState.DONE) item.incrementAge() else item
            }

        val snapshot =
            DailySnapshot(
                scenarioId = scenarioId,
                day = state.currentDay,
                metrics = calculateMetrics(afterAging, ctx.movements),
                movements = ctx.movements.toList(),
            )
        val newState = state.copy(currentDay = SimulationDay(ctx.day + 1), cards = afterAging)

        return SimulationResult(newState = newState, snapshot = snapshot)
    }

    // ─── decisions ──────────────────────────────────────────────────────────

    private fun applyDecisions(
        items: List<Card>,
        decisions: List<Decision>,
        ctx: EngineContext,
    ): List<Card> {
        val current = items.toMutableList()
        decisions.forEach { applyDecision(current, it, ctx) }
        return current
    }

    private fun applyDecision(
        current: MutableList<Card>,
        decision: Decision,
        ctx: EngineContext,
    ) {
        when (decision.type) {
            DecisionType.MOVE_ITEM -> applyMove(current, decision.payload, ctx)
            DecisionType.BLOCK_ITEM -> applyBlock(current, decision.payload, ctx)
            DecisionType.UNBLOCK_ITEM -> applyUnblock(current, decision.payload, ctx)
            DecisionType.ADD_ITEM -> applyAdd(current, decision.payload)
        }
    }

    private fun applyMove(
        current: MutableList<Card>,
        payload: Map<String, String>,
        ctx: EngineContext,
    ) {
        val cardId = payload["cardId"]
        val idx = current.indexOfFirst { it.id == cardId }
        if (idx < 0 || current[idx].state == CardState.DONE) return
        val item = current[idx]
        val advanced = item.advance()
        val movType = if (advanced.state == CardState.DONE) MovementType.COMPLETED else MovementType.MOVED
        current[idx] = advanced
        ctx.movements.add(Movement(type = movType, cardId = item.id, day = SimulationDay(ctx.day), reason = "decision: move"))
    }

    private fun applyBlock(
        current: MutableList<Card>,
        payload: Map<String, String>,
        ctx: EngineContext,
    ) {
        val cardId = payload["cardId"]
        val idx = current.indexOfFirst { it.id == cardId }
        if (idx < 0 || current[idx].state != CardState.IN_PROGRESS) return
        val item = current[idx]
        current[idx] = item.block()
        val reason = payload["reason"] ?: "decision: block"
        ctx.movements.add(Movement(type = MovementType.BLOCKED, cardId = item.id, day = SimulationDay(ctx.day), reason = reason))
    }

    private fun applyUnblock(
        current: MutableList<Card>,
        payload: Map<String, String>,
        ctx: EngineContext,
    ) {
        val cardId = payload["cardId"]
        val idx = current.indexOfFirst { it.id == cardId }
        if (idx < 0 || current[idx].state != CardState.BLOCKED) return
        val item = current[idx]
        current[idx] = item.advance()
        ctx.movements.add(
            Movement(type = MovementType.UNBLOCKED, cardId = item.id, day = SimulationDay(ctx.day), reason = "decision: unblock"),
        )
    }

    private fun applyAdd(
        current: MutableList<Card>,
        payload: Map<String, String>,
    ) {
        val title = payload["title"] ?: return
        val serviceClass =
            payload["serviceClass"]
                ?.let { runCatching { ServiceClass.valueOf(it) }.getOrNull() }
                ?: ServiceClass.STANDARD
        current.add(Card.createSimulation(title, serviceClass))
    }

    // ─── auto-advance ────────────────────────────────────────────────────────

    private fun autoAdvance(
        items: List<Card>,
        policySet: PolicySet,
        ctx: EngineContext,
    ): List<Card> {
        var wipCount = items.count { it.state == CardState.IN_PROGRESS }
        val current = items.toMutableList()
        val orderedTodo = orderTodoByPriority(current, ctx.rng)

        for (idx in orderedTodo) {
            if (wipCount >= policySet.wipLimit) break
            val item = current[idx]
            current[idx] = item.advance()
            ctx.movements.add(
                Movement(type = MovementType.MOVED, cardId = item.id, day = SimulationDay(ctx.day), reason = "auto: started"),
            )
            wipCount++
        }

        return current
    }

    // ─── metrics ─────────────────────────────────────────────────────────────

    private fun calculateMetrics(
        items: List<Card>,
        movements: List<Movement>,
    ): FlowMetrics {
        val nonDoneItems = items.filter { it.state != CardState.DONE }
        val avgAging = if (nonDoneItems.isEmpty()) 0.0 else nonDoneItems.map { it.agingDays.toDouble() }.average()
        return FlowMetrics(
            throughput = movements.count { it.type == MovementType.COMPLETED },
            wipCount = items.count { it.state == CardState.IN_PROGRESS },
            blockedCount = items.count { it.state == CardState.BLOCKED },
            avgAgingDays = avgAging,
        )
    }
}

private data class EngineContext(
    val day: Int,
    val movements: MutableList<Movement>,
    val rng: Random,
)

private fun applyAssignedWorkerExecution(
    items: List<Card>,
    context: SimulationContext?,
    ctx: EngineContext,
): List<Card> {
    if (context == null || context.workerAssignments.isEmpty() || context.steps.isEmpty()) return items

    val current = items.toMutableList()
    context.workerAssignments.forEach { (workerId, stepId) ->
        applySingleWorkerExecution(current, context, WorkerStepAssignment(workerId = workerId, stepId = stepId), ctx)
    }
    return current
}

private data class WorkerStepAssignment(
    val workerId: String,
    val stepId: String,
)

private fun applySingleWorkerExecution(
    current: MutableList<Card>,
    context: SimulationContext,
    assignment: WorkerStepAssignment,
    ctx: EngineContext,
) {
    val worker = context.findWorker(assignment.workerId) ?: return
    val step = context.findStep(assignment.stepId) ?: return
    val targetIndex = findExecutableCardIndex(current, step.id, step.requiredAbility)
    if (targetIndex < 0) return

    val seedMix = ctx.rng.nextLong() xor worker.id.hashCode().toLong() xor step.id.hashCode().toLong()
    val capacities = worker.generateDailyCapacities(random = Random(seedMix))
    val result = step.executeCard(worker = worker, card = current[targetIndex], dailyCapacities = capacities)
    current[targetIndex] = result.updatedCard
}

private fun findExecutableCardIndex(
    cards: List<Card>,
    stepId: String,
    ability: AbilityName,
): Int =
    cards.indexOfFirst { card ->
        card.columnId == stepId &&
            card.state == CardState.IN_PROGRESS &&
            card.remainingEffortFor(ability) > 0
    }

private fun orderTodoByPriority(
    items: List<Card>,
    rng: Random,
): List<Int> {
    val todoIndices = items.indices.filter { items[it].state == CardState.TODO }
    val expedite = todoIndices.filter { items[it].serviceClass == ServiceClass.EXPEDITE }
    val others = todoIndices.filter { items[it].serviceClass != ServiceClass.EXPEDITE }.shuffled(rng)
    return expedite + others
}
