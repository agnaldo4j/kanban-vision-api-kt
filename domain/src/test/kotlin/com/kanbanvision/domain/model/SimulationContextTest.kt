package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimulationContextTest {
    private fun developerWorker(id: String = "worker-dev") =
        Worker(
            id = id,
            name = "Dev",
            abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
        )

    private fun testerWorker(id: String = "worker-tester") =
        Worker(
            id = id,
            name = "Tester",
            abilities =
                setOf(
                    Ability(name = AbilityName.TESTER, seniority = Seniority.PL),
                    Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                ),
        )

    private fun buildContext(
        boardId: String = "board-1",
        steps: List<Step> = emptyList(),
        workerAssignments: Map<String, String> = emptyMap(),
    ): SimulationContext {
        val dev = developerWorker()
        val tester = testerWorker()
        val tribe =
            Tribe(
                name = "Tribe A",
                squads = listOf(Squad(name = "Squad A", workers = listOf(dev, tester))),
            )
        return SimulationContext(
            organizationId = "org-1",
            boardId = boardId,
            steps = steps,
            tribes = listOf(tribe),
            workerAssignments = workerAssignments,
        )
    }

    @Test
    fun `workers flattens tribes squads and workers`() {
        val context = buildContext()
        assertEquals(2, context.workers.size)
        assertNotNull(context.findWorker("worker-dev"))
        assertNotNull(context.findWorker("worker-tester"))
    }

    @Test
    fun `findStep and findWorker return null when not present`() {
        val step = Step(boardId = "board-1", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val context = buildContext(steps = listOf(step))

        assertEquals(step.id, context.findStep(step.id)?.id)
        assertNull(context.findStep("missing-step"))
        assertNull(context.findWorker("missing-worker"))
    }

    @Test
    fun `canAssign checks required ability and existing assignment`() {
        val stepDev = Step(boardId = "board-1", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val stepTest = Step(boardId = "board-1", name = "Test", requiredAbility = AbilityName.TESTER)
        val context =
            buildContext(
                steps = listOf(stepDev, stepTest),
                workerAssignments = mapOf("worker-dev" to stepDev.id),
            )

        assertTrue(context.canAssign(developerWorker(), stepDev))
        assertFalse(context.canAssign(developerWorker(), stepTest))
        assertFalse(context.canAssign(testerWorker(), stepDev))
    }

    @Test
    fun `assign creates assignment when worker and step are valid`() {
        val stepDev = Step(boardId = "board-1", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val context = buildContext(steps = listOf(stepDev))
        val updated = context.assign(developerWorker(), stepDev)

        assertEquals(stepDev.id, updated.workerAssignments["worker-dev"])
    }

    @Test
    fun `assign fails when step belongs to another board`() {
        val stepOtherBoard = Step(boardId = "board-2", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val context = buildContext(boardId = "board-1", steps = listOf(stepOtherBoard))

        assertThrows<IllegalArgumentException> {
            context.assign(developerWorker(), stepOtherBoard)
        }
    }

    @Test
    fun `assign fails when worker lacks required ability`() {
        val stepDev = Step(boardId = "board-1", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val context = buildContext(steps = listOf(stepDev))

        assertThrows<IllegalArgumentException> {
            context.assign(testerWorker(), stepDev)
        }
    }

    @Test
    fun `assign fails when worker is already assigned to another step`() {
        val stepDev = Step(boardId = "board-1", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val stepDeploy = Step(boardId = "board-1", name = "Deploy", requiredAbility = AbilityName.DEPLOYER)
        val context =
            buildContext(
                steps = listOf(stepDev, stepDeploy),
                workerAssignments = mapOf("worker-tester" to stepDeploy.id),
            )

        assertThrows<IllegalArgumentException> {
            context.assign(testerWorker(), stepDev)
        }
    }

    @Test
    fun `context validates non blank organization and board`() {
        assertThrows<IllegalArgumentException> {
            SimulationContext(organizationId = " ", boardId = "board-1")
        }
        assertThrows<IllegalArgumentException> {
            SimulationContext(organizationId = "org-1", boardId = " ")
        }
    }

    @Test
    fun `assign allows reassignment to the same step`() {
        val stepDev = Step(boardId = "board-1", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val context =
            buildContext(
                steps = listOf(stepDev),
                workerAssignments = mapOf("worker-dev" to stepDev.id),
            )

        val updated = context.assign(developerWorker(), stepDev)

        assertEquals(stepDev.id, updated.workerAssignments["worker-dev"])
    }
}
