package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CardValidationAndStateMachineBehaviorTest {
    @Test
    fun `given card constructor invalid inputs when creating card then invariants reject invalid ranges`() {
        assertFailsWith<IllegalArgumentException> { Card(id = "", step = StepRef("s"), title = "x") }
        assertFailsWith<IllegalArgumentException> { Card(step = StepRef(""), title = "x") }
        assertFailsWith<IllegalArgumentException> { Card(step = StepRef("s"), title = "", position = 0) }
        assertFailsWith<IllegalArgumentException> { Card(step = StepRef("s"), title = "x", position = -1) }
        assertFailsWith<IllegalArgumentException> { Card(step = StepRef("s"), title = "x", agingDays = -1) }
        assertFailsWith<IllegalArgumentException> { Card(step = StepRef("s"), title = "x", analysisEffort = -1) }
        assertFailsWith<IllegalArgumentException> { Card(step = StepRef("s"), title = "x", developmentEffort = -1) }
        assertFailsWith<IllegalArgumentException> { Card(step = StepRef("s"), title = "x", testEffort = -1) }
        assertFailsWith<IllegalArgumentException> { Card(step = StepRef("s"), title = "x", deployEffort = -1) }
        assertFailsWith<IllegalArgumentException> {
            Card(step = StepRef("s"), title = "x", analysisEffort = 1, remainingAnalysisEffort = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(step = StepRef("s"), title = "x", developmentEffort = 1, remainingDevelopmentEffort = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(step = StepRef("s"), title = "x", testEffort = 1, remainingTestEffort = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(step = StepRef("s"), title = "x", deployEffort = 1, remainingDeployEffort = 2)
        }
    }

    @Test
    fun `given negative remaining effort below zero when creating card then lower bound invariants reject`() {
        assertFailsWith<IllegalArgumentException> {
            Card(step = StepRef("s"), title = "x", analysisEffort = 5, remainingAnalysisEffort = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(step = StepRef("s"), title = "x", developmentEffort = 5, remainingDevelopmentEffort = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(step = StepRef("s"), title = "x", testEffort = 5, remainingTestEffort = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(step = StepRef("s"), title = "x", deployEffort = 5, remainingDeployEffort = -1)
        }
    }

    @Test
    fun `given card states when advancing then transitions follow domain state machine`() {
        val todo = Card(step = StepRef("s"), title = "x", state = CardState.TODO)
        val inProgress = Card(step = StepRef("s"), title = "x", state = CardState.IN_PROGRESS)
        val blocked = Card(step = StepRef("s"), title = "x", state = CardState.BLOCKED)
        val done = Card(step = StepRef("s"), title = "x", state = CardState.DONE)

        assertEquals(CardState.IN_PROGRESS, todo.advance().state)
        assertEquals(CardState.DONE, inProgress.advance().state)
        assertEquals(CardState.IN_PROGRESS, blocked.advance().state)
        assertEquals(CardState.DONE, done.advance().state)
    }

    @Test
    fun `given ability and card effort when querying remaining effort then matching effort bucket is returned`() {
        val card =
            Card(
                step = StepRef("s"),
                title = "x",
                analysisEffort = 1,
                developmentEffort = 2,
                testEffort = 3,
                deployEffort = 4,
            )

        assertEquals(1, card.remainingEffortFor(AbilityName.PRODUCT_MANAGER))
        assertEquals(2, card.remainingEffortFor(AbilityName.DEVELOPER))
        assertEquals(3, card.remainingEffortFor(AbilityName.TESTER))
        assertEquals(4, card.remainingEffortFor(AbilityName.DEPLOYER))
    }

    @Test
    fun `given target step and position when moving card then location is updated`() {
        val card = Card(step = StepRef("s-1"), title = "Card", position = 0)

        val moved = card.moveTo(targetStep = StepRef("s-2"), newPosition = 3)

        assertEquals("s-2", moved.step.id)
        assertEquals(3, moved.position)
        assertFailsWith<IllegalArgumentException> { card.moveTo(targetStep = StepRef(""), newPosition = 1) }
        assertFailsWith<IllegalArgumentException> { card.moveTo(targetStep = StepRef("s"), newPosition = -1) }
    }
}
