package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrganizationLifecycleSimulatorTest {
    private val createdAt = Instant.parse("2026-03-19T12:00:00Z")
    private val updatedAt = Instant.parse("2026-03-19T12:10:00Z")
    private val deletedAt = Instant.parse("2026-03-19T12:20:00Z")

    private fun developerWorker(): Worker =
        Worker(
            name = "Dev",
            abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            audit = Audit(createdAt = createdAt, updatedAt = updatedAt, deletedAt = null),
        )

    @Test
    fun `ability accepts deleted date equal or after created date`() {
        val ability =
            Ability(
                name = AbilityName.PRODUCT_MANAGER,
                seniority = Seniority.SR,
                audit = Audit(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt),
            )

        assertEquals(AbilityName.PRODUCT_MANAGER, ability.name)
        assertEquals(Seniority.SR, ability.seniority)
        assertEquals(deletedAt, ability.deletedDate)
    }

    @Test
    fun `ability with deleted date before created date throws`() {
        assertThrows<IllegalArgumentException> {
            Ability(
                name = AbilityName.DEVELOPER,
                seniority = Seniority.JR,
                audit = Audit(createdAt = createdAt, updatedAt = updatedAt, deletedAt = createdAt.minusSeconds(1)),
            )
        }
    }

    @Test
    fun `squad exposes lifecycle fields and workers`() {
        val worker = developerWorker()
        val squad =
            Squad(
                name = "Core",
                workers = listOf(worker),
                audit = Audit(createdAt = createdAt, updatedAt = updatedAt, deletedAt = null),
            )

        assertEquals("Core", squad.name)
        assertEquals(1, squad.workers.size)
        assertEquals(createdAt, squad.createdDate)
        assertEquals(updatedAt, squad.updatedDate)
        assertNull(squad.deletedDate)
    }

    @Test
    fun `squad invalid lifecycle fields throw`() {
        val worker = developerWorker()

        assertThrows<IllegalArgumentException> {
            Squad(id = " ", name = "Core", workers = listOf(worker))
        }
        assertThrows<IllegalArgumentException> {
            Squad(
                name = "Core",
                workers = listOf(worker),
                audit = Audit(createdAt = createdAt, updatedAt = createdAt.minusSeconds(1)),
            )
        }
        assertThrows<IllegalArgumentException> {
            Squad(
                name = "Core",
                workers = listOf(worker),
                audit = Audit(createdAt = createdAt, updatedAt = updatedAt, deletedAt = createdAt.minusSeconds(1)),
            )
        }
    }

    @Test
    fun `tribe invalid lifecycle fields throw`() {
        val squad = Squad(name = "Core", workers = listOf(developerWorker()))

        assertThrows<IllegalArgumentException> {
            Tribe(id = " ", name = "Platform", squads = listOf(squad))
        }
        assertThrows<IllegalArgumentException> {
            Tribe(
                name = "Platform",
                squads = listOf(squad),
                audit = Audit(createdAt = createdAt, updatedAt = createdAt.minusSeconds(1)),
            )
        }
        assertThrows<IllegalArgumentException> {
            Tribe(
                name = "Platform",
                squads = listOf(squad),
                audit = Audit(createdAt = createdAt, updatedAt = updatedAt, deletedAt = createdAt.minusSeconds(1)),
            )
        }
    }
}
