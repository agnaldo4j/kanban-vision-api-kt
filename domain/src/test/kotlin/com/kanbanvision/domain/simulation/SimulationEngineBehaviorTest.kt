package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.PolicySet
import com.kanbanvision.domain.model.ScenarioConfig
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.SimulationState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationEngineBehaviorTest {
    private val scenarioId = UUID.randomUUID().toString()
    private val config = ScenarioConfig(wipLimit = 2, teamSize = 3, seedValue = 42L)
    private val emptyState = SimulationState.initial(config)

    private fun inProgress(title: String): Card =
        Card
            .createSimulation(title)
            .advance()

    private fun done(title: String): Card =
        Card
            .createSimulation(title)
            .advance()
            .advance()

    // ─────────────────────────────────────────────
    // Critério de aceite: determinismo
    // ─────────────────────────────────────────────

    @Test
    fun `same input always produces same output (determinism)`() {
        val item = Card.createSimulation("Task A")
        val state = emptyState.copy(cards = listOf(item))

        val result1 = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 42L)
        val result2 = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 42L)

        assertEquals(result1.snapshot.metrics, result2.snapshot.metrics)
        assertEquals(result1.newState.cards.map { it.state }, result2.newState.cards.map { it.state })
        assertEquals(result1.snapshot.movements.size, result2.snapshot.movements.size)
    }

    @Test
    fun `different seeds produce same WIP limit enforcement`() {
        val items = (1..5).map { Card.createSimulation("Task $it") }
        val state = emptyState.copy(cards = items, policySet = PolicySet(wipLimit = 1))

        val result1 = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 1L)
        val result2 = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 99L)

        assertEquals(1, result1.newState.cards.count { it.state == CardState.IN_PROGRESS })
        assertEquals(1, result2.newState.cards.count { it.state == CardState.IN_PROGRESS })
        assertNotNull(result1.newState.cards.firstOrNull { it.state == CardState.IN_PROGRESS })
        assertNotNull(result2.newState.cards.firstOrNull { it.state == CardState.IN_PROGRESS })
    }

    // ─────────────────────────────────────────────
    // Auto-advance e WIP limit
    // ─────────────────────────────────────────────

    @Test
    fun `auto-advance starts TODO items up to WIP limit`() {
        val items = listOf(Card.createSimulation("A"), Card.createSimulation("B"), Card.createSimulation("C"))
        val state = emptyState.copy(cards = items) // wipLimit = 2

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        assertEquals(2, result.newState.cards.count { it.state == CardState.IN_PROGRESS })
        assertEquals(1, result.newState.cards.count { it.state == CardState.TODO })
    }

    @Test
    fun `auto-advance does not exceed WIP limit when items already in progress`() {
        val existing = inProgress("Existing")
        val state = emptyState.copy(cards = listOf(existing, Card.createSimulation("New A"), Card.createSimulation("New B")))

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        assertEquals(2, result.newState.cards.count { it.state == CardState.IN_PROGRESS })
        assertEquals(1, result.newState.cards.count { it.state == CardState.TODO })
    }

    @Test
    fun `auto-advance does not start items when WIP limit already reached`() {
        val state =
            emptyState.copy(
                cards = listOf(inProgress("P1"), inProgress("P2"), Card.createSimulation("Waiting")),
            )

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        assertEquals(2, result.newState.cards.count { it.state == CardState.IN_PROGRESS })
        assertEquals(1, result.newState.cards.count { it.state == CardState.TODO })
    }

    // ─────────────────────────────────────────────
    // Priorização: EXPEDITE primeiro
    // ─────────────────────────────────────────────

    @Test
    fun `EXPEDITE items are auto-advanced before STANDARD within WIP limit`() {
        val standard = Card.createSimulation("Standard task", ServiceClass.STANDARD)
        val expedite = Card.createSimulation("Urgent fix", ServiceClass.EXPEDITE)
        val state =
            emptyState.copy(
                cards = listOf(standard, expedite),
                policySet = PolicySet(wipLimit = 1),
            )

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        val startedItem = result.newState.cards.first { it.state == CardState.IN_PROGRESS }
        assertEquals(ServiceClass.EXPEDITE, startedItem.serviceClass)
        val standardItem = result.newState.cards.first { it.serviceClass == ServiceClass.STANDARD }
        assertEquals(CardState.TODO, standardItem.state)
    }

    // ─────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────

    @Test
    fun `MOVE_ITEM on DONE item is silently ignored`() {
        val item = done("Already done")
        val state = emptyState.copy(cards = listOf(item))

        val result = SimulationEngine.runDay(scenarioId, state, listOf(Decision.move(item.id)), seed = 0L)

        assertEquals(
            CardState.DONE,
            result.newState.cards
                .first()
                .state,
        )
        assertTrue(result.snapshot.movements.none { it.reason == "decision: move" })
        assertEquals(0, result.snapshot.metrics.throughput)
    }
}
