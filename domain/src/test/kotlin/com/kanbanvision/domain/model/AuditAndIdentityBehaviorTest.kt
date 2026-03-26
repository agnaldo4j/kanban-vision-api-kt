package com.kanbanvision.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AuditAndIdentityBehaviorTest {
    @Test
    fun `given fresh audit when created with now then created and updated timestamps are equal`() {
        val instant = Instant.parse("2026-03-20T00:00:00Z")

        val audit = Audit.now(instant)

        assertEquals(instant, audit.createdAt)
        assertEquals(instant, audit.updatedAt)
    }

    @Test
    fun `given existing audit when touched then updated timestamp changes while created is preserved`() {
        val createdAt = Instant.parse("2026-03-20T00:00:00Z")
        val touchedAt = Instant.parse("2026-03-21T00:00:00Z")
        val audit = Audit(createdAt = createdAt, updatedAt = createdAt)

        val touched = audit.touch(touchedAt)

        assertEquals(createdAt, touched.createdAt)
        assertEquals(touchedAt, touched.updatedAt)
        assertNotEquals(audit.updatedAt, touched.updatedAt)
    }

    @Test
    fun `given invalid audit ordering when updated precedes created then validation fails`() {
        val createdAt = Instant.parse("2026-03-21T00:00:00Z")
        val updatedAt = Instant.parse("2026-03-20T00:00:00Z")

        assertFailsWith<IllegalArgumentException> {
            Audit(createdAt = createdAt, updatedAt = updatedAt)
        }
    }

    @Test
    fun `given invalid audit deletion ordering when deleted precedes created then validation fails`() {
        val createdAt = Instant.parse("2026-03-21T00:00:00Z")
        val deletedAt = Instant.parse("2026-03-20T00:00:00Z")

        assertFailsWith<IllegalArgumentException> {
            Audit(createdAt = createdAt, updatedAt = createdAt, deletedAt = deletedAt)
        }
    }

    @Test
    fun `given blank ids in domain entities when constructing then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> { Organization(id = "", name = "Org") }
        assertFailsWith<IllegalArgumentException> { Board(id = "", name = "Board") }
        assertFailsWith<IllegalArgumentException> { Scenario(id = "", name = "Scenario", rules = scenarioRules(), board = board()) }
        assertFailsWith<IllegalArgumentException> {
            Simulation(
                id = "",
                name = "Simulation",
                currentDay = SimulationDay(1),
                status = SimulationStatus.DRAFT,
                organization = organization(),
                scenario = scenario(),
            )
        }
    }

    @Test
    fun `given blank names in domain entities when constructing with valid id then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> { Board(id = "b-1", name = "") }
        assertFailsWith<IllegalArgumentException> { Scenario(id = "sc-1", name = "", rules = scenarioRules(), board = board()) }
        assertFailsWith<IllegalArgumentException> {
            Simulation(
                id = "s-1",
                name = "",
                currentDay = SimulationDay(1),
                status = SimulationStatus.DRAFT,
                organization = organization(),
                scenario = scenario(),
            )
        }
    }

    @Test
    fun `given valid non-null deletedAt when constructing audit then creation succeeds`() {
        val createdAt = Instant.parse("2026-03-20T00:00:00Z")
        val deletedAt = Instant.parse("2026-03-21T00:00:00Z")

        val audit = Audit(createdAt = createdAt, updatedAt = createdAt, deletedAt = deletedAt)

        assertEquals(deletedAt, audit.deletedAt)
    }

    private fun organization(): Organization = Organization.create(name = "Org")

    private fun board(): Board = Board.create(name = "Board")

    private fun scenarioRules(): ScenarioRules = ScenarioRules.create(wipLimit = 2, teamSize = 3, seedValue = 42L)

    private fun scenario(): Scenario = Scenario.create(name = "Scenario", rules = scenarioRules(), board = board())
}
