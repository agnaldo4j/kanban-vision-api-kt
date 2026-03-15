package com.kanbanvision.domain.model.policy

import com.kanbanvision.domain.model.scenario.ScenarioConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PolicySetTest {
    @Test
    fun `valid policy set is created`() {
        val ps = PolicySet(wipLimit = 3)
        assertEquals(3, ps.wipLimit)
    }

    @Test
    fun `wipLimit zero throws`() {
        assertFailsWith<IllegalArgumentException> { PolicySet(wipLimit = 0) }
    }

    @Test
    fun `wipLimit negative throws`() {
        assertFailsWith<IllegalArgumentException> { PolicySet(wipLimit = -1) }
    }

    @Test
    fun `from ScenarioConfig copies wipLimit`() {
        val config = ScenarioConfig(wipLimit = 5, teamSize = 3, seedValue = 42L)
        val ps = PolicySet.from(config)
        assertEquals(5, ps.wipLimit)
    }
}
