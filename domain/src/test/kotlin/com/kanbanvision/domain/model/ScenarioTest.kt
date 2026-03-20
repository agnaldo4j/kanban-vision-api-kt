package com.kanbanvision.domain.model

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScenarioTest {
    private val config = ScenarioConfig(wipLimit = 3, teamSize = 5, seedValue = 42L)

    @Test
    fun `create returns scenario with generated id`() {
        val organizationId = UUID.randomUUID().toString()
        val scenario = Scenario.create(organizationId = organizationId, config = config)
        assertTrue(scenario.id.isNotBlank())
        assertEquals(scenario.id, scenario.boardId)
        assertEquals(organizationId, scenario.organizationId)
        assertEquals(config, scenario.config)
    }

    @Test
    fun `create generates unique ids`() {
        val organizationId = UUID.randomUUID().toString()
        val s1 = Scenario.create(organizationId = organizationId, config = config)
        val s2 = Scenario.create(organizationId = organizationId, config = config)
        assertNotEquals(s1.id, s2.id)
    }

    @Test
    fun `scenario is isolated by organization`() {
        val org1 = UUID.randomUUID().toString()
        val org2 = UUID.randomUUID().toString()
        val s1 = Scenario.create(organizationId = org1, config = config)
        val s2 = Scenario.create(organizationId = org2, config = config)
        assertNotEquals(s1.organizationId, s2.organizationId)
        assertNotEquals(s1.id, s2.id)
    }
}
