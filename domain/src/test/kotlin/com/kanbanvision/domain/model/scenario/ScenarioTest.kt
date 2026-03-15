package com.kanbanvision.domain.model.scenario

import com.kanbanvision.domain.model.valueobjects.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScenarioTest {
    private val config = ScenarioConfig(wipLimit = 3, teamSize = 5, seedValue = 42L)

    @Test
    fun `create returns scenario with generated id`() {
        val tenantId = TenantId.generate()
        val scenario = Scenario.create(tenantId = tenantId, config = config)
        assertTrue(scenario.id.value.isNotBlank())
        assertEquals(tenantId, scenario.tenantId)
        assertEquals(config, scenario.config)
    }

    @Test
    fun `create generates unique ids`() {
        val tenantId = TenantId.generate()
        val s1 = Scenario.create(tenantId = tenantId, config = config)
        val s2 = Scenario.create(tenantId = tenantId, config = config)
        assertNotEquals(s1.id, s2.id)
    }

    @Test
    fun `scenario is isolated by tenant`() {
        val tenant1 = TenantId.generate()
        val tenant2 = TenantId.generate()
        val s1 = Scenario.create(tenantId = tenant1, config = config)
        val s2 = Scenario.create(tenantId = tenant2, config = config)
        assertNotEquals(s1.tenantId, s2.tenantId)
        assertNotEquals(s1.id, s2.id)
    }
}
