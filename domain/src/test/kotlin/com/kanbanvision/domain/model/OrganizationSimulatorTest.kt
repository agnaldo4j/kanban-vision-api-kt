package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class OrganizationSimulatorTest {
    private val worker =
        Worker(
            name = "Joana",
            abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
        )

    @Test
    fun `create squad and tribe with valid data`() {
        val squad = Squad(name = "Core", workers = listOf(worker))
        val tribe = Tribe(name = "Platform", squads = listOf(squad))

        assertEquals("Core", squad.name)
        assertEquals("Platform", tribe.name)
        assertEquals(1, tribe.squads.size)
    }

    @Test
    fun `squad with blank name throws`() {
        assertThrows<IllegalArgumentException> {
            Squad(name = " ", workers = listOf(worker))
        }
    }

    @Test
    fun `tribe with blank name throws`() {
        assertThrows<IllegalArgumentException> {
            Tribe(name = " ", squads = emptyList())
        }
    }
}
