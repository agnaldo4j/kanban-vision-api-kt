package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AbilitySimulatorTest {
    @Test
    fun `ability valid lifecycle fields`() {
        val now = Instant.parse("2026-03-19T12:00:00Z")
        val ability =
            Ability(
                name = AbilityName.DEVELOPER,
                seniority = Seniority.SR,
                audit = Audit(createdAt = now, updatedAt = now, deletedAt = null),
            )

        assertEquals(AbilityName.DEVELOPER, ability.name)
        assertEquals(Seniority.SR, ability.seniority)
        assertNull(ability.deletedDate)
    }

    @Test
    fun `ability with blank id throws`() {
        assertThrows<IllegalArgumentException> {
            Ability(
                id = " ",
                name = AbilityName.DEVELOPER,
                seniority = Seniority.JR,
            )
        }
    }

    @Test
    fun `ability with updatedDate before createdDate throws`() {
        val created = Instant.parse("2026-03-19T12:00:00Z")
        val updated = Instant.parse("2026-03-19T11:59:59Z")
        assertThrows<IllegalArgumentException> {
            Ability(
                name = AbilityName.DEVELOPER,
                seniority = Seniority.PL,
                audit = Audit(createdAt = created, updatedAt = updated),
            )
        }
    }
}
