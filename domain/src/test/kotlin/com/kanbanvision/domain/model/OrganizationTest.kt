package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OrganizationTest {
    @Test
    fun `create returns organization with generated id and given name`() {
        val organization = Organization.create("Acme Corp")
        assertEquals("Acme Corp", organization.name)
        assertTrue(organization.id.isNotBlank())
    }

    @Test
    fun `create with blank name throws`() {
        assertFailsWith<IllegalArgumentException> { Organization.create("") }
    }

    @Test
    fun `create with whitespace-only name throws`() {
        assertFailsWith<IllegalArgumentException> { Organization.create("   ") }
    }

    @Test
    fun `create generates unique ids`() {
        val org1 = Organization.create("Corp A")
        val org2 = Organization.create("Corp B")
        assertNotEquals(org1.id, org2.id)
    }

    @Test
    fun `create supports tribes in aggregate root`() {
        val tribe = Tribe(name = "Core", squads = emptyList())
        val organization = Organization.create(name = "Acme Corp", tribes = listOf(tribe))

        assertEquals(1, organization.tribes.size)
        assertEquals("Core", organization.tribes.first().name)
    }
}
