package com.kanbanvision.domain.model.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScenarioConfigTest {
    @Test
    fun `valid config is created`() {
        val config = ScenarioConfig(wipLimit = 3, teamSize = 5, seedValue = 42L)
        assertEquals(3, config.wipLimit)
        assertEquals(5, config.teamSize)
        assertEquals(42L, config.seedValue)
    }

    @Test
    fun `wipLimit zero throws`() {
        assertFailsWith<IllegalArgumentException> { ScenarioConfig(wipLimit = 0, teamSize = 5, seedValue = 0L) }
    }

    @Test
    fun `wipLimit negative throws`() {
        assertFailsWith<IllegalArgumentException> { ScenarioConfig(wipLimit = -1, teamSize = 5, seedValue = 0L) }
    }

    @Test
    fun `teamSize zero throws`() {
        assertFailsWith<IllegalArgumentException> { ScenarioConfig(wipLimit = 3, teamSize = 0, seedValue = 0L) }
    }

    @Test
    fun `teamSize negative throws`() {
        assertFailsWith<IllegalArgumentException> { ScenarioConfig(wipLimit = 3, teamSize = -1, seedValue = 0L) }
    }
}
