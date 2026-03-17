package com.kanbanvision.httpapi.routes

import com.kanbanvision.httpapi.metrics.DomainMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainMetricsTest {
    @Test
    fun `recordSimulationDayExecuted increments counter`() {
        val registry = SimpleMeterRegistry()
        val metrics = DomainMetrics(registry)

        metrics.recordSimulationDayExecuted()
        metrics.recordSimulationDayExecuted()

        assertEquals(2.0, registry.counter("kanban.simulation.days.executed.total").count())
    }

    @Test
    fun `counter starts at zero`() {
        val registry = SimpleMeterRegistry()
        val metrics = DomainMetrics(registry)

        assertEquals(0.0, registry.counter("kanban.simulation.days.executed.total").count())
    }
}
