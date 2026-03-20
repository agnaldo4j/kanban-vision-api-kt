package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.PolicySet
import com.kanbanvision.domain.model.ScenarioConfig
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.domain.model.WorkItem
import com.kanbanvision.domain.model.WorkItemState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationEngineDecisionsTest {
    private val scenarioId = UUID.randomUUID().toString()
    private val config = ScenarioConfig(wipLimit = 2, teamSize = 3, seedValue = 42L)
    private val emptyState = SimulationState.initial(config)

    private fun inProgress(title: String): WorkItem =
        Card
            .createSimulation(title)
            .advance()

    private fun blocked(title: String): WorkItem =
        Card
            .createSimulation(title)
            .advance()
            .block()

    private fun done(title: String): WorkItem =
        Card
            .createSimulation(title)
            .advance()
            .advance()

    // ─────────────────────────────────────────────
    // Decisões: MOVE, BLOCK, UNBLOCK, ADD
    // ─────────────────────────────────────────────

    @Test
    fun `MOVE_ITEM decision advances item from IN_PROGRESS to DONE and records COMPLETED movement`() {
        val item = inProgress("Task")
        val state = emptyState.copy(cards = listOf(item))

        val result = SimulationEngine.runDay(scenarioId, state, listOf(Decision.move(item.id)), seed = 0L)

        assertEquals(
            WorkItemState.DONE,
            result.newState.cards
                .first()
                .state,
        )
        assertTrue(result.snapshot.movements.any { it.type == MovementType.COMPLETED })
        assertEquals(1, result.snapshot.metrics.throughput)
    }

    @Test
    fun `MOVE_ITEM decision on TODO item advances to IN_PROGRESS and records MOVED movement`() {
        val item = Card.createSimulation("Task")
        val state = emptyState.copy(cards = listOf(item))

        val result = SimulationEngine.runDay(scenarioId, state, listOf(Decision.move(item.id)), seed = 0L)

        assertEquals(
            WorkItemState.IN_PROGRESS,
            result.newState.cards
                .first()
                .state,
        )
        assertTrue(result.snapshot.movements.any { it.type == MovementType.MOVED })
    }

    @Test
    fun `BLOCK_ITEM decision blocks IN_PROGRESS item and records BLOCKED movement`() {
        val item = inProgress("Task")
        val state = emptyState.copy(cards = listOf(item))
        val decisions = listOf(Decision.block(item.id, "external dependency"))

        val result = SimulationEngine.runDay(scenarioId, state, decisions, seed = 0L)

        assertEquals(
            WorkItemState.BLOCKED,
            result.newState.cards
                .first()
                .state,
        )
        val blockedMov = result.snapshot.movements.first { it.type == MovementType.BLOCKED }
        assertEquals("external dependency", blockedMov.reason)
        assertEquals(1, result.snapshot.metrics.blockedCount)
    }

    @Test
    fun `UNBLOCK_ITEM decision unblocks BLOCKED item and records UNBLOCKED movement`() {
        val item = blocked("Task")
        val state = emptyState.copy(cards = listOf(item))

        val result = SimulationEngine.runDay(scenarioId, state, listOf(Decision.unblock(item.id)), seed = 0L)

        assertEquals(
            WorkItemState.IN_PROGRESS,
            result.newState.cards
                .first()
                .state,
        )
        assertTrue(result.snapshot.movements.any { it.type == MovementType.UNBLOCKED })
    }

    @Test
    fun `ADD_ITEM decision adds new work item and it is auto-started within WIP limit`() {
        val decisions = listOf(Decision.addItem("New story", "EXPEDITE"))

        val result = SimulationEngine.runDay(scenarioId, emptyState, decisions, seed = 0L)

        assertEquals(1, result.newState.cards.size)
        val added = result.newState.cards.first()
        assertEquals("New story", added.title)
        assertEquals(ServiceClass.EXPEDITE, added.serviceClass)
        assertEquals(WorkItemState.IN_PROGRESS, added.state)
    }

    @Test
    fun `ADD_ITEM with invalid serviceClass defaults to STANDARD`() {
        val decision =
            Decision(
                id = UUID.randomUUID().toString(),
                type = com.kanbanvision.domain.model.DecisionType.ADD_ITEM,
                payload = mapOf("title" to "Legacy item", "serviceClass" to "NOT_A_CLASS"),
            )

        val result = SimulationEngine.runDay(scenarioId, emptyState, listOf(decision), seed = 0L)
        val added = result.newState.cards.first()

        assertEquals(1, result.newState.cards.size)
        assertEquals(ServiceClass.STANDARD, added.serviceClass)
    }

    @Test
    fun `legacy payload workItemId is still accepted`() {
        val item = inProgress("Legacy")
        val state = emptyState.copy(cards = listOf(item))
        val legacyMove =
            Decision(
                id = UUID.randomUUID().toString(),
                type = com.kanbanvision.domain.model.DecisionType.MOVE_ITEM,
                payload = mapOf("workItemId" to item.id),
            )

        val result = SimulationEngine.runDay(scenarioId, state, listOf(legacyMove), seed = 0L)
        val updated = result.newState.cards.first()

        assertEquals(WorkItemState.DONE, updated.state)
        assertTrue(result.snapshot.movements.any { it.type == MovementType.COMPLETED && it.cardId == item.id })
    }

    @Test
    fun `unknown cardId in decision is silently ignored`() {
        val result = SimulationEngine.runDay(scenarioId, emptyState, listOf(Decision.move("nonexistent-id")), seed = 0L)

        assertTrue(result.newState.cards.isEmpty())
        assertTrue(result.snapshot.movements.isEmpty())
    }

    // ─────────────────────────────────────────────
    // Aging
    // ─────────────────────────────────────────────

    @Test
    fun `aging increments for all non-DONE items each day`() {
        val state =
            emptyState.copy(
                cards =
                    listOf(
                        Card.createSimulation("A"),
                        inProgress("B"),
                        blocked("C"),
                        done("D"),
                    ),
                policySet = PolicySet(wipLimit = 10),
            )

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        val byTitle = result.newState.cards.associateBy { it.title }
        assertTrue(byTitle["A"]!!.agingDays >= 1)
        assertTrue(byTitle["B"]!!.agingDays >= 1)
        assertTrue(byTitle["C"]!!.agingDays >= 1)
        assertEquals(0, byTitle["D"]!!.agingDays)
    }

    // ─────────────────────────────────────────────
    // Métricas e estado do dia
    // ─────────────────────────────────────────────

    @Test
    fun `currentDay advances by 1 after runDay`() {
        val result = SimulationEngine.runDay(scenarioId, emptyState, emptyList(), seed = 0L)
        assertEquals(2, result.newState.currentDay.value)
    }

    @Test
    fun `snapshot day matches the state day before execution`() {
        val state = emptyState.copy(currentDay = SimulationDay(5))
        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)
        assertEquals(5, result.snapshot.day.value)
        assertEquals(6, result.newState.currentDay.value)
    }

    @Test
    fun `metrics wipCount reflects items in progress at end of day`() {
        val items = listOf(Card.createSimulation("A"), Card.createSimulation("B"))
        val state = emptyState.copy(cards = items) // wipLimit = 2 → both auto-start

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        assertEquals(2, result.snapshot.metrics.wipCount)
        assertEquals(0, result.snapshot.metrics.blockedCount)
        assertEquals(0, result.snapshot.metrics.throughput)
    }

    @Test
    fun `avgAgingDays is zero when all items are DONE`() {
        val state = emptyState.copy(cards = listOf(done("Task")))

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        assertEquals(0.0, result.snapshot.metrics.avgAgingDays)
    }
}
