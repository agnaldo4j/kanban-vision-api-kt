package com.kanbanvision.domain.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkerCapacityAndExecutionEligibilityBehaviorTest {
    @Test
    fun `given invalid worker setup when creating worker then constructor guards are enforced`() {
        assertFailsWith<IllegalArgumentException> { Worker(id = "", name = "x", abilities = setOf(ability(AbilityName.DEVELOPER))) }
        assertFailsWith<IllegalArgumentException> { Worker(name = "", abilities = setOf(ability(AbilityName.DEVELOPER))) }
        assertFailsWith<IllegalArgumentException> { Worker(name = "x", abilities = emptySet()) }
    }

    @Test
    fun `given step and worker abilities when checking execution eligibility then can execute mirrors ability match`() {
        val developer = Worker(name = "Dev", abilities = setOf(ability(AbilityName.DEVELOPER)))
        val tester = Worker(name = "Tester", abilities = setOf(ability(AbilityName.TESTER), ability(AbilityName.DEPLOYER)))
        val devStep = Step.create(board = BoardRef("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)

        assertTrue(developer.canExecute(devStep))
        assertFalse(tester.canExecute(devStep))
    }

    @Test
    fun `given invalid random capacity boundaries when generating capacities then input validation fails`() {
        val worker = Worker(name = "Dev", abilities = setOf(ability(AbilityName.DEVELOPER)))

        assertFailsWith<IllegalArgumentException> {
            worker.generateDailyCapacities(random = Random(1), minPoints = -1, maxPoints = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            worker.generateDailyCapacities(random = Random(1), minPoints = 2, maxPoints = 1)
        }
    }

    @Test
    fun `given same random seed when generating capacities then generation is deterministic for non deploy abilities`() {
        val worker =
            Worker(
                name = "Multi",
                abilities =
                    setOf(
                        ability(AbilityName.PRODUCT_MANAGER),
                        ability(AbilityName.DEVELOPER),
                        ability(AbilityName.TESTER),
                        ability(AbilityName.DEPLOYER),
                    ),
            )

        val first = worker.generateDailyCapacities(random = Random(42), minPoints = 0, maxPoints = 4)
        val second = worker.generateDailyCapacities(random = Random(42), minPoints = 0, maxPoints = 4)

        assertEquals(first[AbilityName.PRODUCT_MANAGER], second[AbilityName.PRODUCT_MANAGER])
        assertEquals(first[AbilityName.DEVELOPER], second[AbilityName.DEVELOPER])
        assertEquals(first[AbilityName.TESTER], second[AbilityName.TESTER])
        assertEquals(Int.MAX_VALUE, first[AbilityName.DEPLOYER])
    }

    private fun ability(name: AbilityName): Ability = Ability(name = name, seniority = Seniority.PL)
}
