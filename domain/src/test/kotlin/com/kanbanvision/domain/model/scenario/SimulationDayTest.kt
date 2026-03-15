package com.kanbanvision.domain.model.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SimulationDayTest {
    @Test
    fun `day 1 is valid`() {
        val day = SimulationDay(1)
        assertEquals(1, day.value)
    }

    @Test
    fun `day greater than 1 is valid`() {
        val day = SimulationDay(100)
        assertEquals(100, day.value)
    }

    @Test
    fun `day 0 throws`() {
        assertFailsWith<IllegalArgumentException> { SimulationDay(0) }
    }

    @Test
    fun `negative day throws`() {
        assertFailsWith<IllegalArgumentException> { SimulationDay(-1) }
    }
}
