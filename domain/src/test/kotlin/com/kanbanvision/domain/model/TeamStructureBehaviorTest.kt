package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TeamStructureBehaviorTest {
    @Test
    fun `given squad aggregate when reading components then team contract remains explicit`() {
        val worker = devWorker("w-1")
        val squad = Squad(name = "Platform", workers = listOf(worker))

        assertTrue(squad.component1().isNotBlank())
        assertEquals("Platform", squad.component2())
        assertEquals(1, squad.component3().size)
        assertEquals(squad.component4(), squad.copy().audit)

        assertFailsWith<IllegalArgumentException> { Squad(id = "", name = "Platform", workers = listOf(worker)) }
        assertFailsWith<IllegalArgumentException> { Squad(id = "sq-1", name = "", workers = listOf(worker)) }
        assertTrue(Squad(id = "sq-1", name = "Platform", workers = emptyList()).workers.isEmpty())
    }

    @Test
    fun `given tribe aggregate when reading components then structure contract remains explicit`() {
        val squad = Squad(name = "Core", workers = listOf(devWorker("w-2")))
        val tribe = Tribe(name = "Payments", squads = listOf(squad))

        assertTrue(tribe.component1().isNotBlank())
        assertEquals("Payments", tribe.component2())
        assertEquals(1, tribe.component3().size)
        assertEquals(tribe.component4(), tribe.copy().audit)

        assertFailsWith<IllegalArgumentException> { Tribe(id = "", name = "Payments", squads = listOf(squad)) }
        assertFailsWith<IllegalArgumentException> { Tribe(id = "tr-1", name = "", squads = listOf(squad)) }
        assertTrue(Tribe(id = "tr-1", name = "Payments", squads = emptyList()).squads.isEmpty())
    }

    @Test
    fun `given simulation day value object when reading primitive and text then contract is explicit`() {
        val day = SimulationDay(3)
        assertEquals(3, day.value)
        assertEquals("SimulationDay(value=3)", day.toString())
        assertTrue(day.hashCode() != 0)
    }

    private fun devWorker(id: String): Worker =
        Worker(
            id = id,
            name = "Dev",
            abilities =
                setOf(
                    Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL),
                    Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                ),
        )
}
