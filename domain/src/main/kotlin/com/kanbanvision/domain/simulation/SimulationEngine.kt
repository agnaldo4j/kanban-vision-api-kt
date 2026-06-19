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
import com.kanbanvision.domain.model.toRef
import java.time.Instant
import kotlin.random.Random

object SimulationEngine {
    fun runDay(
        simulation: Simulation,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult {
        val ctx = EngineContext(day = simulation.currentDay.value, seed = seed, now = Instant.now())
        val rng = Random(seed)
        val scenario = simulation.scenario

        val initialCards = scenario.board.steps.flatMap { it.cards }
        val (afterDecisions, movDecisions) = applyDecisions(initialCards, scenario.board, decisions, ctx)
        val (afterAutoAdvance, movAutoAdvance) = autoAdvance(afterDecisions, scenario.rules.policySet.wipLimit, rng, ctx)
        val afterExecution = applyAssignedWorkerExecution(afterAutoAdvance, scenario.board.steps, ctx)
        val afterAging = afterExecution.map { card -> if (card.state != CardState.DONE) card.incrementAge() else card }

        return buildResult(simulation, decisions, afterAging, movDecisions + movAutoAdvance)
    }

    private fun buildResult(
        simulation: Simulation,
        decisions: List<Decision>,
        afterAging: List<Card>,
        allMovements: List<Movement>,
    ): SimulationResult {
        val scenario = simulation.scenario
        val snapshot =
            DailySnapshot(
                simulation = simulation.toRef(),
                scenario = scenario.toRef(),
                day = simulation.currentDay,
                metrics = calculateMetrics(afterAging, allMovements),
                movements = allMovements,
            )
        val updatedScenario =
            scenario.copy(
                board = scenario.board.withCards(afterAging),
                decisions = scenario.decisions + decisions,
                history = scenario.history + snapshot,
            )
        val updatedSimulation = simulation.copy(currentDay = SimulationDay(simulation.currentDay.value + 1), scenario = updatedScenario)
        return SimulationResult(simulation = updatedSimulation, snapshot = snapshot)
    }

    private fun applyDecisions(
        cards: List<Card>,
        board: Board,
        decisions: List<Decision>,
        ctx: EngineContext,
    ): Pair<List<Card>, List<Movement>> {
        val current = cards.toMutableList()
        val movements = mutableListOf<Movement>()
        decisions.forEach { decision ->
            when (decision.type) {
                DecisionType.MOVE_ITEM -> applyMove(current, decision.payload, ctx.day)?.let { movements += it }
                DecisionType.BLOCK_ITEM -> applyBlock(current, decision.payload, ctx.day)?.let { movements += it }
                DecisionType.UNBLOCK_ITEM -> applyUnblock(current, decision.payload, ctx.day)?.let { movements += it }
                DecisionType.ADD_ITEM -> applyAdd(current, board, decision.payload)
            }
        }
        return current.toList() to movements.toList()
    }

    private fun autoAdvance(
        cards: List<Card>,
        wipLimit: Int,
        rng: Random,
        ctx: EngineContext,
    ): Pair<List<Card>, List<Movement>> {
        var wipCount = cards.count { it.state == CardState.IN_PROGRESS }
        val current = cards.toMutableList()
        val movements = mutableListOf<Movement>()
        val orderedTodo = orderTodoByPriority(current, rng)
        for (idx in orderedTodo) {
            if (wipCount >= wipLimit) break
            val card = current[idx]
            current[idx] = card.advance()
            movements.add(Movement(type = MovementType.MOVED, cardId = card.id, day = SimulationDay(ctx.day), reason = "auto: started"))
            wipCount++
        }
        return current.toList() to movements.toList()
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
        return current.toList()
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
                card.step.id == step.id &&
                    card.state == CardState.IN_PROGRESS &&
                    card.remainingEffortFor(step.requiredAbility) > 0
            }
        if (targetIndex < 0) return
        val seedMix = stableExecutionSeed(ctx.seed, ctx.day, worker.id, step.id)
        val capacities = worker.generateDailyCapacities(random = Random(seedMix))
        val result = step.executeCard(worker = worker, card = current[targetIndex], dailyCapacities = capacities, now = ctx.now)
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

private fun applyMove(
    current: MutableList<Card>,
    payload: Map<String, String>,
    day: Int,
): Movement? {
    val cardId = payload["cardId"] ?: return null
    val idx = current.indexOfFirst { it.id == cardId }
    if (idx < 0 || current[idx].state == CardState.DONE) return null
    val card = current[idx]
    val advanced = card.advance()
    val movementType = if (advanced.state == CardState.DONE) MovementType.COMPLETED else MovementType.MOVED
    current[idx] = advanced
    return Movement(type = movementType, cardId = card.id, day = SimulationDay(day), reason = "decision: move")
}

private fun applyBlock(
    current: MutableList<Card>,
    payload: Map<String, String>,
    day: Int,
): Movement? {
    val cardId = payload["cardId"] ?: return null
    val idx = current.indexOfFirst { it.id == cardId }
    if (idx < 0 || current[idx].state != CardState.IN_PROGRESS) return null
    val card = current[idx]
    current[idx] = card.block()
    val reason = payload["reason"] ?: "decision: block"
    return Movement(type = MovementType.BLOCKED, cardId = card.id, day = SimulationDay(day), reason = reason)
}

private fun applyUnblock(
    current: MutableList<Card>,
    payload: Map<String, String>,
    day: Int,
): Movement? {
    val cardId = payload["cardId"] ?: return null
    val idx = current.indexOfFirst { it.id == cardId }
    if (idx < 0 || current[idx].state != CardState.BLOCKED) return null
    val card = current[idx]
    current[idx] = card.advance()
    return Movement(
        type = MovementType.UNBLOCKED,
        cardId = card.id,
        day = SimulationDay(day),
        reason = "decision: unblock",
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
    val position = current.count { it.step.id == firstStep.id }
    current.add(Card.create(step = firstStep.toRef(), title = title, position = position).copy(serviceClass = serviceClass))
}

private data class EngineContext(
    val day: Int,
    val seed: Long,
    val now: Instant,
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
    val fixedDate = todoIndices.filter { cards[it].serviceClass == ServiceClass.FIXED_DATE }
    val standard = todoIndices.filter { cards[it].serviceClass == ServiceClass.STANDARD }.shuffled(rng)
    val intangible = todoIndices.filter { cards[it].serviceClass == ServiceClass.INTANGIBLE }.shuffled(rng)
    return expedite + fixedDate + standard + intangible
}

private fun Board.withCards(cards: List<Card>): Board {
    val cardsByStep = cards.groupBy { it.step.id }
    val updatedSteps =
        steps.map { step ->
            val stepCards = cardsByStep[step.id].orEmpty().sortedBy { it.position }
            step.copy(cards = stepCards)
        }
    return copy(steps = updatedSteps)
}
