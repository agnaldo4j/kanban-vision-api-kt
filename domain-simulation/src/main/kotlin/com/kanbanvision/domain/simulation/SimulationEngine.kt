package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.StepId
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationResult
import java.time.Instant
import kotlin.random.Random

object SimulationEngine {
    fun runDay(
        simulation: Simulation,
        decisions: List<Decision>,
        seed: Long,
        now: Instant,
    ): SimulationResult {
        val ctx = EngineContext(day = simulation.currentDay.value, seed = seed, now = now)
        val rng = Random(seed)
        val scenario = simulation.scenario

        val initialCards = scenario.board.allCards()
        val (afterDecisions, movDecisions) = applyDecisions(initialCards, scenario.board, decisions, ctx)
        val (afterAutoAdvance, movAutoAdvance) = autoAdvance(afterDecisions, scenario.rules.policySet.wipLimit, rng, ctx)
        val afterExecution = applyAssignedWorkerExecution(afterAutoAdvance, scenario.board.stepsInExecutionOrder(), ctx)
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
        val updatedScenario = scenario.copy(board = scenario.board.redistributeCards(afterAging))
        val updatedSimulation =
            simulation
                .advanceDay()
                .appendDecisions(decisions)
                .appendSnapshot(snapshot)
                .copy(scenario = updatedScenario)
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
            when (decision) {
                is Decision.Unknown -> Unit
                is Decision.MoveItem -> applyMove(current, decision.cardId, ctx.day)?.let { movements += it }
                is Decision.BlockItem -> applyBlock(current, decision.cardId, decision.reason, ctx.day)?.let { movements += it }
                is Decision.UnblockItem -> applyUnblock(current, decision.cardId, ctx.day)?.let { movements += it }
                is Decision.AddItem -> applyAdd(current, board, decision.title.value, decision.serviceClass)
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
        stepsInExecutionOrder: List<Step>,
        ctx: EngineContext,
    ): List<Card> {
        if (stepsInExecutionOrder.isEmpty()) return cards
        val current = cards.toMutableList()
        stepsInExecutionOrder.forEach { step ->
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
                card.step == step.id &&
                    card.state == CardState.IN_PROGRESS &&
                    card.remainingEffortFor(step.requiredAbility) > 0
            }
        if (targetIndex < 0) return
        val seedMix = stableExecutionSeed(ctx.seed, ctx.day, worker.id, step.id)
        val capacities = worker.generateDailyCapacities(random = Random(seedMix))
        step
            .executeCard(worker = worker, card = current[targetIndex], dailyCapacities = capacities, now = ctx.now)
            .onRight { current[targetIndex] = it.updatedCard }
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

private val CardState.advancesOnMoveDecision: Boolean
    get() =
        when (this) {
            CardState.TODO, CardState.IN_PROGRESS -> true
            CardState.BLOCKED, CardState.DONE -> false
        }

private fun applyMove(
    current: MutableList<Card>,
    cardId: CardId,
    day: Int,
): Movement? {
    val idx = current.indexOfFirst { it.id == cardId }
    if (idx < 0 || !current[idx].state.advancesOnMoveDecision) return null
    val card = current[idx]
    val advanced = card.advance()
    val movementType = if (advanced.state == CardState.DONE) MovementType.COMPLETED else MovementType.MOVED
    current[idx] = advanced
    return Movement(type = movementType, cardId = card.id, day = SimulationDay(day), reason = "decision: move")
}

private fun applyBlock(
    current: MutableList<Card>,
    cardId: CardId,
    reason: String,
    day: Int,
): Movement? {
    val idx = current.indexOfFirst { it.id == cardId }
    if (idx < 0 || current[idx].state != CardState.IN_PROGRESS) return null
    val card = current[idx]
    card.block().onRight { current[idx] = it }
    return Movement(type = MovementType.BLOCKED, cardId = card.id, day = SimulationDay(day), reason = reason)
}

private fun applyUnblock(
    current: MutableList<Card>,
    cardId: CardId,
    day: Int,
): Movement? {
    val idx = current.indexOfFirst { it.id == cardId }
    if (idx < 0 || current[idx].state != CardState.BLOCKED) return null
    val card = current[idx]
    card.unblock().onRight { current[idx] = it }
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
    title: String,
    serviceClass: ServiceClass,
) {
    val firstStep = board.firstStep() ?: return
    val position = current.count { it.step == firstStep.id }
    current.add(Card.create(step = firstStep.toRef(), title = title, position = position, serviceClass = serviceClass))
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
    stepId: StepId,
): Long =
    listOf(simulationSeed, day.toLong(), workerId.hashCode().toLong(), stepId.hashCode().toLong())
        .fold(STABLE_HASH_SEED) { acc, value -> acc * STABLE_HASH_MULTIPLIER + value }

private fun orderTodoByPriority(
    cards: List<Card>,
    rng: Random,
): List<Int> {
    val todoIndices = cards.indices.filter { cards[it].state == CardState.TODO }
    return ServiceClass.entries
        .sortedBy { it.schedulingRank }
        .flatMap { serviceClass ->
            val tier = todoIndices.filter { cards[it].serviceClass == serviceClass }
            if (serviceClass.shuffleWithinTier) tier.shuffled(rng) else tier
        }
}
