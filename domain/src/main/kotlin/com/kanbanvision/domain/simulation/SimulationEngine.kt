package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.DecisionType
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationResult
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.Worker
import kotlin.random.Random

object SimulationEngine {
    fun runDay(
        simulation: Simulation,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult {
        val scenario = simulation.scenario
        val day = simulation.currentDay.value
        val ctx = EngineContext(day = day, seed = seed, movements = mutableListOf(), rng = Random(seed))

        val initialCards = scenario.board.steps.flatMap { it.cards }
        val afterDecisions = applyDecisions(initialCards, scenario.board, decisions, ctx)
        val afterAutoAdvance = autoAdvance(afterDecisions, scenario.rules.policySet.wipLimit, ctx)
        val afterExecution = applyAssignedWorkerExecution(afterAutoAdvance, scenario.board.steps, ctx)
        val afterAging = afterExecution.map { card -> if (card.state != CardState.DONE) card.incrementAge() else card }

        val snapshot =
            DailySnapshot(
                simulationId = simulation.id,
                day = simulation.currentDay,
                metrics = calculateMetrics(afterAging, ctx.movements),
                movements = ctx.movements.toList(),
            )

        val updatedBoard = scenario.board.withCards(afterAging)
        val updatedScenario =
            scenario.copy(
                board = updatedBoard,
                decisions = scenario.decisions + decisions,
                history = scenario.history + snapshot,
            )
        val updatedSimulation = simulation.copy(currentDay = SimulationDay(day + 1), scenario = updatedScenario)

        return SimulationResult(simulation = updatedSimulation, snapshot = snapshot)
    }

    private fun applyDecisions(
        cards: List<Card>,
        board: Board,
        decisions: List<Decision>,
        ctx: EngineContext,
    ): List<Card> {
        val current = cards.toMutableList()
        decisions.forEach { decision ->
            when (decision.type) {
                DecisionType.MOVE_ITEM -> applyMove(current, decision.payload, ctx)
                DecisionType.BLOCK_ITEM -> applyBlock(current, decision.payload, ctx)
                DecisionType.UNBLOCK_ITEM -> applyUnblock(current, decision.payload, ctx)
                DecisionType.ADD_ITEM -> applyAdd(current, board, decision.payload)
            }
        }
        return current
    }

    private fun applyMove(
        current: MutableList<Card>,
        payload: Map<String, String>,
        ctx: EngineContext,
    ) {
        val cardId = payload["cardId"] ?: return
        val idx = current.indexOfFirst { it.id == cardId }
        if (idx < 0 || current[idx].state == CardState.DONE) return
        val card = current[idx]
        val advanced = card.advance()
        val movementType = if (advanced.state == CardState.DONE) MovementType.COMPLETED else MovementType.MOVED
        current[idx] = advanced
        ctx.movements.add(Movement(type = movementType, cardId = card.id, day = SimulationDay(ctx.day), reason = "decision: move"))
    }

    private fun applyBlock(
        current: MutableList<Card>,
        payload: Map<String, String>,
        ctx: EngineContext,
    ) {
        val cardId = payload["cardId"] ?: return
        val idx = current.indexOfFirst { it.id == cardId }
        if (idx < 0 || current[idx].state != CardState.IN_PROGRESS) return
        val card = current[idx]
        current[idx] = card.block()
        val reason = payload["reason"] ?: "decision: block"
        ctx.movements.add(Movement(type = MovementType.BLOCKED, cardId = card.id, day = SimulationDay(ctx.day), reason = reason))
    }

    private fun applyUnblock(
        current: MutableList<Card>,
        payload: Map<String, String>,
        ctx: EngineContext,
    ) {
        val cardId = payload["cardId"] ?: return
        val idx = current.indexOfFirst { it.id == cardId }
        if (idx < 0 || current[idx].state != CardState.BLOCKED) return
        val card = current[idx]
        current[idx] = card.advance()
        ctx.movements.add(
            Movement(
                type = MovementType.UNBLOCKED,
                cardId = card.id,
                day = SimulationDay(ctx.day),
                reason = "decision: unblock",
            ),
        )
    }

    private fun applyAdd(
        current: MutableList<Card>,
        board: Board,
        payload: Map<String, String>,
    ) {
        val title = payload["title"] ?: return
        val firstStep = board.steps.minByOrNull { it.position } ?: return
        val serviceClass =
            payload["serviceClass"]
                ?.let { runCatching { ServiceClass.valueOf(it) }.getOrNull() }
                ?: ServiceClass.STANDARD
        val position = current.count { it.stepId == firstStep.id }
        current.add(Card.create(stepId = firstStep.id, title = title, position = position).copy(serviceClass = serviceClass))
    }

    private fun autoAdvance(
        cards: List<Card>,
        wipLimit: Int,
        ctx: EngineContext,
    ): List<Card> {
        var wipCount = cards.count { it.state == CardState.IN_PROGRESS }
        val current = cards.toMutableList()
        val orderedTodo = orderTodoByPriority(current, ctx.rng)

        for (idx in orderedTodo) {
            if (wipCount >= wipLimit) break
            val card = current[idx]
            current[idx] = card.advance()
            ctx.movements.add(Movement(type = MovementType.MOVED, cardId = card.id, day = SimulationDay(ctx.day), reason = "auto: started"))
            wipCount++
        }
        return current
    }

    private fun applyAssignedWorkerExecution(
        cards: List<Card>,
        steps: List<Step>,
        ctx: EngineContext,
    ): List<Card> {
        if (steps.isEmpty()) return cards

        val current = cards.toMutableList()
        steps
            .sortedBy { it.position }
            .forEach { step ->
                step.workers.sortedBy { it.id }.forEach { worker ->
                    applySingleWorkerExecution(current, step, worker, ctx)
                }
            }
        return current
    }

    private fun applySingleWorkerExecution(
        current: MutableList<Card>,
        step: Step,
        worker: Worker,
        ctx: EngineContext,
    ) {
        if (!worker.hasAbility(step.requiredAbility)) return

        val targetIndex =
            current.indexOfFirst { card ->
                card.stepId == step.id &&
                    card.state == CardState.IN_PROGRESS &&
                    card.remainingEffortFor(step.requiredAbility) > 0
            }
        if (targetIndex < 0) return

        val seedMix = stableExecutionSeed(ctx.seed, ctx.day, worker.id, step.id)
        val capacities = worker.generateDailyCapacities(random = Random(seedMix))
        val result = step.executeCard(worker = worker, card = current[targetIndex], dailyCapacities = capacities)
        current[targetIndex] = result.updatedCard
    }

    private fun calculateMetrics(
        cards: List<Card>,
        movements: List<Movement>,
    ): FlowMetrics {
        val nonDone = cards.filter { it.state != CardState.DONE }
        val avgAging = if (nonDone.isEmpty()) 0.0 else nonDone.map { it.agingDays.toDouble() }.average()
        return FlowMetrics(
            throughput = movements.count { it.type == MovementType.COMPLETED },
            wipCount = cards.count { it.state == CardState.IN_PROGRESS },
            blockedCount = cards.count { it.state == CardState.BLOCKED },
            avgAgingDays = avgAging,
        )
    }
}

private data class EngineContext(
    val day: Int,
    val seed: Long,
    val movements: MutableList<Movement>,
    val rng: Random,
)

private const val STABLE_HASH_SEED = 17L
private const val STABLE_HASH_MULTIPLIER = 31L

private fun stableExecutionSeed(
    simulationSeed: Long,
    day: Int,
    workerId: String,
    stepId: String,
): Long =
    listOf(simulationSeed, day.toLong(), workerId.hashCode().toLong(), stepId.hashCode().toLong())
        .fold(STABLE_HASH_SEED) { acc, value -> acc * STABLE_HASH_MULTIPLIER + value }

private fun orderTodoByPriority(
    cards: List<Card>,
    rng: Random,
): List<Int> {
    val todoIndices = cards.indices.filter { cards[it].state == CardState.TODO }
    val expedite = todoIndices.filter { cards[it].serviceClass == ServiceClass.EXPEDITE }
    val others = todoIndices.filter { cards[it].serviceClass != ServiceClass.EXPEDITE }.shuffled(rng)
    return expedite + others
}

private fun Board.withCards(cards: List<Card>): Board {
    val cardsByStep = cards.groupBy { it.stepId }
    val updatedSteps =
        steps.map { step ->
            val stepCards = cardsByStep[step.id].orEmpty().sortedBy { it.position }
            step.copy(cards = stepCards)
        }
    return copy(steps = updatedSteps)
}
