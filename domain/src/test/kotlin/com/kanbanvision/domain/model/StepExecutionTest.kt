package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepExecutionTest {
    @Test
    fun `step execution consumes partial effort when daily capacity is not enough`() {
        val step = Step(name = "Development", requiredAbility = AbilityName.DEVELOPER)
        val worker =
            Worker(
                name = "Dev 1",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val card =
            SimulatorCard(
                title = "Card A",
                analysisEffort = 0,
                developmentEffort = 8,
                testEffort = 0,
                deployEffort = 0,
            )

        val result = step.executeCard(worker, card, dailyCapacities = mapOf(AbilityName.DEVELOPER to 3))

        assertEquals(3, result.consumedEffort)
        assertEquals(5, result.updatedCard.remainingDevelopmentEffort)
        assertFalse(result.isStepCompleted)
    }

    @Test
    fun `step execution completes when effort reaches zero`() {
        val step = Step(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
        val worker =
            Worker(
                name = "PM 1",
                abilities = setOf(Ability(name = AbilityName.PRODUCT_MANAGER, seniority = Seniority.SR)),
            )
        val card =
            SimulatorCard(
                title = "Card B",
                analysisEffort = 2,
                developmentEffort = 0,
                testEffort = 0,
                deployEffort = 0,
            )

        val result = step.executeCard(worker, card, dailyCapacities = mapOf(AbilityName.PRODUCT_MANAGER to 4))

        assertEquals(2, result.consumedEffort)
        assertEquals(0, result.updatedCard.remainingAnalysisEffort)
        assertTrue(result.isStepCompleted)
    }

    @Test
    fun `deployer step always consumes all remaining deploy effort`() {
        val step = Step(name = "Deploy", requiredAbility = AbilityName.DEPLOYER)
        val worker =
            Worker(
                name = "Tester 1",
                abilities =
                    setOf(
                        Ability(name = AbilityName.TESTER, seniority = Seniority.PL),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                    ),
            )
        val card =
            SimulatorCard(
                title = "Card C",
                analysisEffort = 0,
                developmentEffort = 0,
                testEffort = 0,
                deployEffort = 7,
            )

        val result = step.executeCard(worker, card, dailyCapacities = mapOf(AbilityName.DEPLOYER to 0))

        assertEquals(7, result.consumedEffort)
        assertEquals(0, result.updatedCard.remainingDeployEffort)
        assertTrue(result.isStepCompleted)
    }

    @Test
    fun `step execution with no remaining effort consumes zero`() {
        val step = Step(name = "Deploy", requiredAbility = AbilityName.DEPLOYER)
        val worker =
            Worker(
                name = "Tester 2",
                abilities =
                    setOf(
                        Ability(name = AbilityName.TESTER, seniority = Seniority.PL),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                    ),
            )
        val card =
            SimulatorCard(
                title = "Card D",
                analysisEffort = 0,
                developmentEffort = 0,
                testEffort = 0,
                deployEffort = 0,
            )

        val result = step.executeCard(worker, card, dailyCapacities = mapOf(AbilityName.DEPLOYER to 0))

        assertEquals(0, result.consumedEffort)
        assertTrue(result.isStepCompleted)
    }

    @Test
    fun `step execution fails when worker lacks required ability`() {
        val step = Step(name = "Development", requiredAbility = AbilityName.DEVELOPER)
        val worker =
            Worker(
                name = "PM 2",
                abilities = setOf(Ability(name = AbilityName.PRODUCT_MANAGER, seniority = Seniority.PL)),
            )
        val card =
            SimulatorCard(
                title = "Card E",
                analysisEffort = 0,
                developmentEffort = 5,
                testEffort = 0,
                deployEffort = 0,
            )

        assertThrows<IllegalArgumentException> {
            step.executeCard(worker, card, dailyCapacities = mapOf(AbilityName.DEVELOPER to 5))
        }
    }
}
