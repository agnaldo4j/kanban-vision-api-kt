package com.kanbanvision.domain.model.simulator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkerSimulatorTest {
    @Test
    fun `worker with tester must also have deployer`() {
        assertThrows<IllegalArgumentException> {
            Worker(
                name = "Alice",
                abilities = setOf(Ability(name = AbilityName.TESTER, seniority = Seniority.PL)),
            )
        }
    }

    @Test
    fun `daily capacities are generated for worker abilities`() {
        val worker =
            Worker(
                name = "Bruno",
                abilities =
                    setOf(
                        Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                    ),
            )

        val capacities = worker.generateDailyCapacities(random = Random(42), minPoints = 0, maxPoints = 10)

        assertTrue(capacities.getValue(AbilityName.DEVELOPER) in 0..10)
        assertEquals(Int.MAX_VALUE, capacities.getValue(AbilityName.DEPLOYER))
        assertEquals(0, capacities.getValue(AbilityName.TESTER))
        assertEquals(0, capacities.getValue(AbilityName.PRODUCT_MANAGER))
    }

    @Test
    fun `worker cannot execute step with missing ability`() {
        val step = Step(name = "Development", requiredAbility = AbilityName.DEVELOPER)
        val worker =
            Worker(
                name = "Carla",
                abilities = setOf(Ability(name = AbilityName.PRODUCT_MANAGER, seniority = Seniority.SR)),
            )

        assertFalse(step.canAssign(worker))
        assertThrows<IllegalArgumentException> { step.ensureCanAssign(worker) }
    }

    @Test
    fun `worker with updatedDate before createdDate throws`() {
        assertThrows<IllegalArgumentException> {
            Worker(
                name = "Bad Dates",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.JR)),
                createdDate = Instant.parse("2026-03-19T12:00:00Z"),
                updatedDate = Instant.parse("2026-03-19T11:00:00Z"),
            )
        }
    }

    @Test
    fun `generateDailyCapacities with invalid range throws`() {
        val worker =
            Worker(
                name = "Range Tester",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )

        assertThrows<IllegalArgumentException> {
            worker.generateDailyCapacities(random = Random(1), minPoints = 5, maxPoints = 4)
        }
    }
}
