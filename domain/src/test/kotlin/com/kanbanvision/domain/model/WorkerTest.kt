package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkerTest {
    private val boardId = UUID.randomUUID().toString()

    @Test
    fun `worker can execute card in step with matching ability`() {
        val worker =
            Worker(
                name = "Ana",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val step =
            Step.create(
                boardId = boardId,
                name = "Development",
                position = 1,
                requiredAbility = AbilityName.DEVELOPER,
            )

        assertTrue(worker.canExecute(step))
        step.ensureCanAssign(worker)
    }

    @Test
    fun `worker cannot execute card in step with non-matching ability`() {
        val worker =
            Worker(
                name = "Bruno",
                abilities =
                    setOf(
                        Ability(name = AbilityName.TESTER, seniority = Seniority.SR),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.SR),
                    ),
            )
        val step =
            Step.create(
                boardId = boardId,
                name = "Development",
                position = 1,
                requiredAbility = AbilityName.DEVELOPER,
            )

        assertFalse(worker.canExecute(step))
        assertThrows<IllegalArgumentException> { step.ensureCanAssign(worker) }
    }

    @Test
    fun `worker cannot execute card in step when required ability does not match`() {
        val worker =
            Worker(
                name = "Carla",
                abilities = setOf(Ability(name = AbilityName.PRODUCT_MANAGER, seniority = Seniority.JR)),
            )
        val step =
            Step.create(
                boardId = boardId,
                name = "Development",
                position = 0,
                requiredAbility = AbilityName.DEVELOPER,
            )

        assertFalse(worker.canExecute(step))
        assertThrows<IllegalArgumentException> { step.ensureCanAssign(worker) }
    }

    @Test
    fun `worker with blank name throws`() {
        assertThrows<IllegalArgumentException> {
            Worker(
                name = " ",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        }
    }

    @Test
    fun `worker without abilities throws`() {
        assertThrows<IllegalArgumentException> {
            Worker(name = "Diego", abilities = emptySet())
        }
    }

    @Test
    fun `worker with tester ability and no deployer throws`() {
        assertThrows<IllegalArgumentException> {
            Worker(
                name = "Fabio",
                abilities = setOf(Ability(name = AbilityName.TESTER, seniority = Seniority.PL)),
            )
        }
    }
}
