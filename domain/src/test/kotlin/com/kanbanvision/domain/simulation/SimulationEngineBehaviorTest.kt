package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.policy.PolicySet
import com.kanbanvision.domain.model.scenario.ScenarioConfig
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.workitem.ServiceClass
import com.kanbanvision.domain.model.workitem.WorkItem
import com.kanbanvision.domain.model.workitem.WorkItemState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SimulationEngineBehaviorTest {
    private val scenarioId = ScenarioId.generate()
    private val config = ScenarioConfig(wipLimit = 2, teamSize = 3, seedValue = 42L)
    private val emptyState = SimulationState.initial(config)

    private fun inProgress(title: String): WorkItem =
        WorkItem
            .create(title)
            .advance()

    // ─────────────────────────────────────────────
    // Critério de aceite: determinismo
    // ─────────────────────────────────────────────

    @Test
    fun `same input always produces same output (determinism)`() {
        val item = WorkItem.create("Task A")
        val state = emptyState.copy(items = listOf(item))

        val result1 = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 42L)
        val result2 = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 42L)

        assertEquals(result1.snapshot.metrics, result2.snapshot.metrics)
        assertEquals(result1.newState.items.map { it.state }, result2.newState.items.map { it.state })
        assertEquals(result1.snapshot.movements.size, result2.snapshot.movements.size)
    }

    @Test
    fun `different seeds produce same WIP limit enforcement`() {
        val items = (1..5).map { WorkItem.create("Task $it") }
        val state = emptyState.copy(items = items, policySet = PolicySet(wipLimit = 1))

        val result1 = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 1L)
        val result2 = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 99L)

        assertEquals(1, result1.newState.items.count { it.state == WorkItemState.IN_PROGRESS })
        assertEquals(1, result2.newState.items.count { it.state == WorkItemState.IN_PROGRESS })
        assertNotNull(result1.newState.items.firstOrNull { it.state == WorkItemState.IN_PROGRESS })
        assertNotNull(result2.newState.items.firstOrNull { it.state == WorkItemState.IN_PROGRESS })
    }

    // ─────────────────────────────────────────────
    // Auto-advance e WIP limit
    // ─────────────────────────────────────────────

    @Test
    fun `auto-advance starts TODO items up to WIP limit`() {
        val items = listOf(WorkItem.create("A"), WorkItem.create("B"), WorkItem.create("C"))
        val state = emptyState.copy(items = items) // wipLimit = 2

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        assertEquals(2, result.newState.items.count { it.state == WorkItemState.IN_PROGRESS })
        assertEquals(1, result.newState.items.count { it.state == WorkItemState.TODO })
    }

    @Test
    fun `auto-advance does not exceed WIP limit when items already in progress`() {
        val existing = inProgress("Existing")
        val state = emptyState.copy(items = listOf(existing, WorkItem.create("New A"), WorkItem.create("New B")))

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        assertEquals(2, result.newState.items.count { it.state == WorkItemState.IN_PROGRESS })
        assertEquals(1, result.newState.items.count { it.state == WorkItemState.TODO })
    }

    @Test
    fun `auto-advance does not start items when WIP limit already reached`() {
        val state =
            emptyState.copy(
                items = listOf(inProgress("P1"), inProgress("P2"), WorkItem.create("Waiting")),
            )

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        assertEquals(2, result.newState.items.count { it.state == WorkItemState.IN_PROGRESS })
        assertEquals(1, result.newState.items.count { it.state == WorkItemState.TODO })
    }

    // ─────────────────────────────────────────────
    // Priorização: EXPEDITE primeiro
    // ─────────────────────────────────────────────

    @Test
    fun `EXPEDITE items are auto-advanced before STANDARD within WIP limit`() {
        val standard = WorkItem.create("Standard task", ServiceClass.STANDARD)
        val expedite = WorkItem.create("Urgent fix", ServiceClass.EXPEDITE)
        val state =
            emptyState.copy(
                items = listOf(standard, expedite),
                policySet = PolicySet(wipLimit = 1),
            )

        val result = SimulationEngine.runDay(scenarioId, state, emptyList(), seed = 0L)

        val startedItem = result.newState.items.first { it.state == WorkItemState.IN_PROGRESS }
        assertEquals(ServiceClass.EXPEDITE, startedItem.serviceClass)
        val standardItem = result.newState.items.first { it.serviceClass == ServiceClass.STANDARD }
        assertEquals(WorkItemState.TODO, standardItem.state)
    }
}
