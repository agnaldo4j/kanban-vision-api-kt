package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    @Test
    fun `initial state can bind domain context with organization and board`() {
        val scenario = Scenario.create(organizationId = "org-1", config = config)
        val worker =
            Worker(
                name = "Dev",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val tribe =
            Tribe(
                name = "Core",
                squads =
                    listOf(
                        Squad(
                            name = "API",
                            workers = listOf(worker),
                        ),
                    ),
            )

        val state = SimulationState.initial(scenario = scenario, config = config, tribes = listOf(tribe))

        val context = assertNotNull(state.context)
        assertEquals(scenario.organizationId, context.organizationId)
        assertEquals(scenario.boardId, context.boardId)
        assertEquals(1, context.workers.size)
        assertEquals(worker.id, context.workers.first().id)
    }

    @Test
    fun `initial state with scenario uses empty tribes by default`() {
        val scenario = Scenario.create(organizationId = "org-2", config = config)

        val state = SimulationState.initial(scenario = scenario, config = config)

        val context = assertNotNull(state.context)
        assertEquals(scenario.organizationId, context.organizationId)
        assertEquals(scenario.boardId, context.boardId)
        assertTrue(context.tribes.isEmpty())
        assertTrue(context.workers.isEmpty())
        assertTrue(context.workerAssignments.isEmpty())
    }
}
