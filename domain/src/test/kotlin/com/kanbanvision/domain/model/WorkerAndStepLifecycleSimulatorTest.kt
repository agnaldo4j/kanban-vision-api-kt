package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkerAndStepLifecycleSimulatorTest {
    private val createdAt = Instant.parse("2026-03-19T12:00:00Z")
    private val updatedAt = Instant.parse("2026-03-19T12:10:00Z")

    @Test
    fun `worker validates lifecycle and required fields`() {
        assertThrows<IllegalArgumentException> {
            Worker(
                id = " ",
                name = "Ana",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        }
        assertThrows<IllegalArgumentException> {
            Worker(
                name = " ",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        }
        assertThrows<IllegalArgumentException> {
            Worker(name = "Ana", abilities = emptySet())
        }
        assertThrows<IllegalArgumentException> {
            Worker(
                name = "Ana",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
                audit = Audit(createdAt = createdAt, updatedAt = updatedAt, deletedAt = createdAt.minusSeconds(1)),
            )
        }
    }

    @Test
    fun `worker ability checks and capacity defaults work as expected`() {
        val worker =
            Worker(
                name = "Bruna",
                abilities =
                    setOf(
                        Ability(name = AbilityName.DEVELOPER, seniority = Seniority.JR),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.JR),
                    ),
            )

        assertTrue(worker.hasAbility(AbilityName.DEVELOPER))
        assertFalse(worker.hasAbility(AbilityName.TESTER))

        val capacities = worker.generateDailyCapacities(random = Random(7))
        assertTrue(capacities.getValue(AbilityName.DEVELOPER) in 0..10)
        assertEquals(Int.MAX_VALUE, capacities.getValue(AbilityName.DEPLOYER))
    }

    @Test
    fun `worker capacities reject negative minimum`() {
        val worker =
            Worker(
                name = "Carlos",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )

        assertThrows<IllegalArgumentException> {
            worker.generateDailyCapacities(random = Random(3), minPoints = -1, maxPoints = 5)
        }
    }

    @Test
    fun `step validates lifecycle fields`() {
        assertThrows<IllegalArgumentException> {
            Step(id = " ", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        }
        assertThrows<IllegalArgumentException> {
            Step(name = " ", requiredAbility = AbilityName.DEVELOPER)
        }
        assertThrows<IllegalArgumentException> {
            Step(
                name = "Dev",
                requiredAbility = AbilityName.DEVELOPER,
                audit = Audit(createdAt = createdAt, updatedAt = createdAt.minusSeconds(1)),
            )
        }
        assertThrows<IllegalArgumentException> {
            Step(
                name = "Dev",
                requiredAbility = AbilityName.DEVELOPER,
                audit = Audit(createdAt = createdAt, updatedAt = updatedAt, deletedAt = createdAt.minusSeconds(1)),
            )
        }
    }

    @Test
    fun `step execute uses zero when daily capacity map has no required ability`() {
        val worker =
            Worker(
                name = "Dana",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )

        val step = Step(name = "Development", requiredAbility = AbilityName.DEVELOPER)
        val card =
            Card(
                title = "Card missing key",
                analysisEffort = 0,
                developmentEffort = 2,
                testEffort = 0,
                deployEffort = 0,
            )

        val result = step.executeCard(worker, card, dailyCapacities = emptyMap())
        assertEquals(0, result.consumedEffort)
        assertEquals(2, result.updatedCard.remainingDevelopmentEffort)
        assertFalse(result.isStepCompleted)
    }

    @Test
    fun `seniority entries are available`() {
        assertTrue(Seniority.entries.contains(Seniority.JR))
        assertTrue(Seniority.entries.contains(Seniority.PL))
        assertTrue(Seniority.entries.contains(Seniority.SR))
    }
}
