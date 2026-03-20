package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class OrganizationTest {
    private val worker =
        Worker(
            name = "Eva",
            abilities =
                setOf(
                    Ability(name = AbilityName.TESTER, seniority = Seniority.PL),
                    Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                ),
        )

    @Test
    fun `create squad with workers`() {
        val squad = Squad(name = "Payments", workers = listOf(worker))
        assertEquals("Payments", squad.name)
        assertEquals(1, squad.workers.size)
    }

    @Test
    fun `create tribe with squads`() {
        val squad = Squad(name = "Core", workers = listOf(worker))
        val tribe = Tribe(name = "Platform", squads = listOf(squad))
        assertEquals("Platform", tribe.name)
        assertEquals(1, tribe.squads.size)
    }

    @Test
    fun `squad with blank name throws`() {
        assertThrows<IllegalArgumentException> { Squad(name = " ", workers = listOf(worker)) }
    }

    @Test
    fun `tribe with blank name throws`() {
        assertThrows<IllegalArgumentException> { Tribe(name = " ", squads = emptyList()) }
    }
}
