package com.kanbanvision.domain.model.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlowMetricsTest {
    @Test
    fun `valid metrics created`() {
        val m = FlowMetrics(throughput = 2, wipCount = 3, blockedCount = 1, avgAgingDays = 4.5)
        assertEquals(2, m.throughput)
        assertEquals(3, m.wipCount)
        assertEquals(1, m.blockedCount)
        assertEquals(4.5, m.avgAgingDays)
    }

    @Test
    fun `zero values are valid`() {
        val m = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
        assertEquals(0, m.throughput)
    }

    @Test
    fun `negative throughput throws`() {
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(throughput = -1, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
        }
    }

    @Test
    fun `negative wipCount throws`() {
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(throughput = 0, wipCount = -1, blockedCount = 0, avgAgingDays = 0.0)
        }
    }

    @Test
    fun `negative avgAgingDays throws`() {
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = -0.1)
        }
    }
}
