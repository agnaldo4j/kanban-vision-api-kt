package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.metrics.DomainMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainMetricsTest {
    @Test
    fun `given domain metrics when recording simulation day then counter is incremented`() {
        val registry = SimpleMeterRegistry()
        val metrics = DomainMetrics(registry)

        metrics.recordSimulationDayExecuted()
        metrics.recordSimulationDayExecuted()

        val counter = registry.get("kanban.simulation.days.executed").counter()
        assertEquals(2.0, counter.count())
    }
}
