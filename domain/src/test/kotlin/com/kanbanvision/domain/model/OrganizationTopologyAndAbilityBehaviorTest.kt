package com.kanbanvision.domain.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrganizationTopologyAndAbilityBehaviorTest {
    @Test
    fun `given tribe hierarchy when organization is created then structure is preserved`() {
        val worker = worker(name = "Ana", abilities = setOf(ability(AbilityName.DEVELOPER)))
        val squad = Squad(name = "Payments", workers = listOf(worker))
        val tribe = Tribe(name = "Core", squads = listOf(squad))

        val organization = Organization.create(name = "Acme", tribes = listOf(tribe))

        assertEquals("Acme", organization.name)
        assertEquals(1, organization.tribes.size)
        assertEquals("Core", organization.tribes.first().name)
        assertEquals(
            "Payments",
            organization.tribes
                .first()
                .squads
                .first()
                .name,
        )
        assertEquals(
            "Ana",
            organization.tribes
                .first()
                .squads
                .first()
                .workers
                .first()
                .name,
        )
    }

    @Test
    fun `given tester worker without deployer ability when creating worker then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            worker(name = "QA", abilities = setOf(ability(AbilityName.TESTER)))
        }
    }

    @Test
    fun `given step ability requirement when assigning worker then only compatible worker is accepted`() {
        val developer = worker(name = "Dev", abilities = setOf(ability(AbilityName.DEVELOPER)))
        val tester = worker(name = "Test", abilities = setOf(ability(AbilityName.TESTER), ability(AbilityName.DEPLOYER)))
        val step = Step.create(boardId = "board-1", name = "Development", position = 0, requiredAbility = AbilityName.DEVELOPER)

        val assigned = step.assignWorker(developer)

        assertTrue(assigned.workers.any { it.id == developer.id })
        assertFalse(step.canAssign(tester))
        assertFailsWith<IllegalArgumentException> { step.assignWorker(tester) }
    }

    @Test
    fun `given worker abilities and deterministic random seed when generating daily capacities then rules are enforced`() {
        val worker =
            worker(
                name = "Multi",
                abilities =
                    setOf(
                        ability(AbilityName.DEVELOPER, Seniority.SR),
                        ability(AbilityName.TESTER, Seniority.PL),
                        ability(AbilityName.DEPLOYER, Seniority.PL),
                    ),
            )

        val capacities = worker.generateDailyCapacities(random = Random(7), minPoints = 1, maxPoints = 3)

        assertEquals(0, capacities[AbilityName.PRODUCT_MANAGER])
        assertTrue(capacities.getValue(AbilityName.DEVELOPER) in 1..3)
        assertTrue(capacities.getValue(AbilityName.TESTER) in 1..3)
        assertEquals(Int.MAX_VALUE, capacities[AbilityName.DEPLOYER])
    }

    private fun ability(
        name: AbilityName,
        seniority: Seniority = Seniority.PL,
    ): Ability = Ability(name = name, seniority = seniority)

    private fun worker(
        name: String,
        abilities: Set<Ability>,
    ): Worker = Worker(name = name, abilities = abilities)
}
