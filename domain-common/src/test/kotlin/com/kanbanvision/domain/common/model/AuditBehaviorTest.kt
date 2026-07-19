package com.kanbanvision.domain.common.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AuditBehaviorTest {
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
    fun `given no arguments when using audit defaults then now and touch fall back to the current instant`() {
        val default = Audit()
        assertEquals(Instant.EPOCH, default.createdAt)
        assertEquals(default.createdAt, default.updatedAt)

        val fresh = Audit.now()
        assertEquals(fresh.createdAt, fresh.updatedAt)

        val touched = fresh.touch()
        assertEquals(fresh.createdAt, touched.createdAt)
    }

    @Test
    fun `given valid non-null deletedAt when constructing audit then creation succeeds`() {
        val createdAt = Instant.parse("2026-03-20T00:00:00Z")
        val deletedAt = Instant.parse("2026-03-21T00:00:00Z")

        val audit = Audit(createdAt = createdAt, updatedAt = createdAt, deletedAt = deletedAt)

        assertEquals(deletedAt, audit.deletedAt)
    }
}
