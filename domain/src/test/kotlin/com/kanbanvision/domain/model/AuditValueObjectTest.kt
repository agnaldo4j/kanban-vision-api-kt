package com.kanbanvision.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuditValueObjectTest {
    @Test
    fun `default audit uses epoch`() {
        val audit = Audit()
        assertEquals(Instant.EPOCH, audit.createdAt)
        assertEquals(Instant.EPOCH, audit.updatedAt)
        assertNull(audit.deletedAt)
    }

    @Test
    fun `companion now sets created and updated to same instant`() {
        val audit = Audit.now(Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), audit.createdAt)
        assertEquals(audit.createdAt, audit.updatedAt)
    }

    @Test
    fun `touch updates updatedAt and keeps createdAt`() {
        val audit = Audit.now()
        val touched = audit.touch()
        assertEquals(audit.createdAt, touched.createdAt)
    }
}
