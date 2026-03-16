package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.decision.Decision
import com.kanbanvision.domain.model.decision.DecisionType
import com.kanbanvision.domain.model.metrics.FlowMetrics
import com.kanbanvision.domain.model.movement.Movement
import com.kanbanvision.domain.model.movement.MovementType
import com.kanbanvision.domain.model.policy.PolicySet
import com.kanbanvision.domain.model.scenario.DailySnapshot
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.scenario.SimulationResult
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.workitem.ServiceClass
import com.kanbanvision.domain.model.workitem.WorkItem
import com.kanbanvision.domain.model.workitem.WorkItemState
import kotlin.random.Random

object SimulationEngine {
    /**
     * Runs a single simulation day. Pure function: same inputs always produce the same output.
     * [seed] controls ordering of non-EXPEDITE TODO items during auto-advance;
     * EXPEDITE items are always prioritized first in their original list order.
     */
    fun runDay(
        scenarioId: ScenarioId,
        state: SimulationState,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult {
        val ctx = EngineContext(day = state.currentDay.value, movements = mutableListOf(), rng = Random(seed))

        val afterDecisions = applyDecisions(state.items, decisions, ctx)
        val afterAutoAdvance = autoAdvance(afterDecisions, state.policySet, ctx)
        val afterAging =
            afterAutoAdvance.map { item ->
                if (item.state != WorkItemState.DONE) item.incrementAge() else item
            }

        val snapshot =
            DailySnapshot(
                scenarioId = scenarioId,
                day = state.currentDay,
                metrics = calculateMetrics(afterAging, ctx.movements),
                movements = ctx.movements.toList(),
            )
        val newState = state.copy(currentDay = SimulationDay(ctx.day + 1), items = afterAging)

        return SimulationResult(newState = newState, snapshot = snapshot)
    }

    // ─── context ────────────────────────────────────────────────────────────

    private data class EngineContext(
        val day: Int,
        val movements: MutableList<Movement>,
        val rng: Random,
    )

    // ─── decisions ──────────────────────────────────────────────────────────

    private fun applyDecisions(
        items: List<WorkItem>,
        decisions: List<Decision>,
        ctx: EngineContext,
    ): List<WorkItem> {
        val current = items.toMutableList()
        decisions.forEach { applyDecision(current, it, ctx) }
        return current
    }

    private fun applyDecision(
        current: MutableList<WorkItem>,
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
        current: MutableList<WorkItem>,
        payload: Map<String, String>,
        ctx: EngineContext,
    ) {
        val idx = current.indexOfFirst { it.id.value == payload["workItemId"] }
        if (idx < 0 || current[idx].state == WorkItemState.DONE) return
        val item = current[idx]
        val advanced = item.advance()
        val movType = if (advanced.state == WorkItemState.DONE) MovementType.COMPLETED else MovementType.MOVED
        current[idx] = advanced
        ctx.movements.add(Movement(type = movType, workItemId = item.id, day = SimulationDay(ctx.day), reason = "decision: move"))
    }

    private fun applyBlock(
        current: MutableList<WorkItem>,
        payload: Map<String, String>,
        ctx: EngineContext,
    ) {
        val idx = current.indexOfFirst { it.id.value == payload["workItemId"] }
        if (idx < 0 || current[idx].state != WorkItemState.IN_PROGRESS) return
        val item = current[idx]
        current[idx] = item.block()
        val reason = payload["reason"] ?: "decision: block"
        ctx.movements.add(Movement(type = MovementType.BLOCKED, workItemId = item.id, day = SimulationDay(ctx.day), reason = reason))
    }

    private fun applyUnblock(
        current: MutableList<WorkItem>,
        payload: Map<String, String>,
        ctx: EngineContext,
    ) {
        val idx = current.indexOfFirst { it.id.value == payload["workItemId"] }
        if (idx < 0 || current[idx].state != WorkItemState.BLOCKED) return
        val item = current[idx]
        current[idx] = item.advance()
        ctx.movements.add(
            Movement(type = MovementType.UNBLOCKED, workItemId = item.id, day = SimulationDay(ctx.day), reason = "decision: unblock"),
        )
    }

    private fun applyAdd(
        current: MutableList<WorkItem>,
        payload: Map<String, String>,
    ) {
        val title = payload["title"] ?: return
        val serviceClass =
            payload["serviceClass"]
                ?.let { runCatching { ServiceClass.valueOf(it) }.getOrNull() }
                ?: ServiceClass.STANDARD
        current.add(WorkItem.create(title, serviceClass))
    }

    // ─── auto-advance ────────────────────────────────────────────────────────

    private fun autoAdvance(
        items: List<WorkItem>,
        policySet: PolicySet,
        ctx: EngineContext,
    ): List<WorkItem> {
        var wipCount = items.count { it.state == WorkItemState.IN_PROGRESS }
        val current = items.toMutableList()
        val orderedTodo = orderTodoByPriority(current, ctx.rng)

        for (idx in orderedTodo) {
            if (wipCount >= policySet.wipLimit) break
            val item = current[idx]
            current[idx] = item.advance()
            ctx.movements.add(
                Movement(type = MovementType.MOVED, workItemId = item.id, day = SimulationDay(ctx.day), reason = "auto: started"),
            )
            wipCount++
        }

        return current
    }

    private fun orderTodoByPriority(
        items: List<WorkItem>,
        rng: Random,
    ): List<Int> {
        val todoIndices = items.indices.filter { items[it].state == WorkItemState.TODO }
        val expedite = todoIndices.filter { items[it].serviceClass == ServiceClass.EXPEDITE }
        val others = todoIndices.filter { items[it].serviceClass != ServiceClass.EXPEDITE }.shuffled(rng)
        return expedite + others
    }

    // ─── metrics ─────────────────────────────────────────────────────────────

    private fun calculateMetrics(
        items: List<WorkItem>,
        movements: List<Movement>,
    ): FlowMetrics {
        val nonDoneItems = items.filter { it.state != WorkItemState.DONE }
        val avgAging = if (nonDoneItems.isEmpty()) 0.0 else nonDoneItems.map { it.agingDays.toDouble() }.average()
        return FlowMetrics(
            throughput = movements.count { it.type == MovementType.COMPLETED },
            wipCount = items.count { it.state == WorkItemState.IN_PROGRESS },
            blockedCount = items.count { it.state == WorkItemState.BLOCKED },
            avgAgingDays = avgAging,
        )
    }
}
