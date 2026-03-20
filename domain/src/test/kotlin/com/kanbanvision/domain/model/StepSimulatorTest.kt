package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepSimulatorTest {
    @Test
    fun `worker can only be assigned to one step at a time`() {
        val analysis = Step(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
        val development = Step(name = "Development", requiredAbility = AbilityName.PRODUCT_MANAGER)
        val worker =
            Worker(
                name = "Dani",
                abilities = setOf(Ability(name = AbilityName.PRODUCT_MANAGER, seniority = Seniority.PL)),
            )

        val afterFirst = analysis.assignWorkerByWorkerId(worker, emptyMap())
        assertEquals(analysis.id, afterFirst[worker.id])

        assertThrows<IllegalArgumentException> {
            development.assignWorkerByWorkerId(worker, afterFirst)
        }
    }

    @Test
    fun `worker already assigned to same step can be re-assigned`() {
        val step = Step(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
        val worker =
            Worker(
                name = "Same Step",
                abilities = setOf(Ability(name = AbilityName.PRODUCT_MANAGER, seniority = Seniority.JR)),
            )

        val first = step.assignWorkerByWorkerId(worker, emptyMap())
        val second = step.assignWorkerByWorkerId(worker, first)

        assertEquals(step.id, second[worker.id])
        assertTrue(second.containsKey(worker.id))
    }
}
