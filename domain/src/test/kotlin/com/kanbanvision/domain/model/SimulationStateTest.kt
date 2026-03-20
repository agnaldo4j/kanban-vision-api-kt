package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationStateTest {
    private val config = ScenarioConfig(wipLimit = 3, teamSize = 2, seedValue = 0L)

    @Test
    fun `initial state starts on day 1 with empty items`() {
        val state = SimulationState.initial(config)
        assertEquals(1, state.currentDay.value)
        assertTrue(state.cards.isEmpty())
        assertEquals(3, state.policySet.wipLimit)
    }

    @Test
    fun `copy advances currentDay`() {
        val state = SimulationState.initial(config)
        val next = state.copy(currentDay = SimulationDay(2))
        assertEquals(2, next.currentDay.value)
    }

    @Test
    fun `copy with items preserves policySet`() {
        val state = SimulationState.initial(config)
        val item = Card.createSimulation("Task")
        val withItem = state.copy(cards = listOf(item))
        assertEquals(1, withItem.cards.size)
        assertEquals(PolicySet(wipLimit = 3), withItem.policySet)
    }
}
