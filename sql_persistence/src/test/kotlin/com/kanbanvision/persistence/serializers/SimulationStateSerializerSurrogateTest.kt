package com.kanbanvision.persistence.serializers

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SimulationStateSerializerSurrogateTest {
    @Test
    fun `surrogate data classes expose stable generated methods`() {
        val ability = AbilitySurrogate(id = "a1", name = "DEVELOPER", seniority = "PL")
        val worker = WorkerSurrogate(id = "w1", name = "Worker", abilities = listOf(ability))
        val squad = SquadSurrogate(id = "s1", name = "Squad", workers = listOf(worker))
        val tribe = TribeSurrogate(id = "t1", name = "Tribe", squads = listOf(squad))
        val step = StepSurrogate(id = "st1", boardId = "b1", name = "Dev", position = 1, requiredAbility = "DEVELOPER")
        val item = WorkItemSurrogate(id = "c1", title = "Card", serviceClass = "STANDARD", state = "TODO", agingDays = 0)
        val context =
            SimulationContextSurrogate(
                organizationId = "org-1",
                boardId = "b1",
                steps = listOf(step),
                tribes = listOf(tribe),
            )
        val state = SimulationStateSurrogate(currentDay = 1, wipLimit = 2, cards = listOf(item), context = context)

        assertEquals("a1", ability.component1())
        assertEquals("w1", worker.component1())
        assertEquals("s1", squad.component1())
        assertEquals("t1", tribe.component1())
        assertEquals("st1", step.component1())
        assertEquals("c1", item.component1())
        assertEquals("org-1", context.component1())
        assertEquals(1, state.component1())
    }

    @Test
    fun `surrogate copy hash and string are consistent`() {
        val base = WorkItemSurrogate(id = "c1", title = "Card", serviceClass = "STANDARD", state = "TODO", agingDays = 1)
        val copied = base.copy(title = "Card 2", position = 3)

        assertNotEquals(base, copied)
        assertNotEquals(base.hashCode(), copied.hashCode())
        assertTrue(base.toString().contains("Card"))
        assertTrue(copied.toString().contains("Card 2"))
        assertEquals(3, copied.position)
        assertEquals("c1", copied.id)
    }
}
