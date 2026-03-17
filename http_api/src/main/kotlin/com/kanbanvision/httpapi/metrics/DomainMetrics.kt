package com.kanbanvision.httpapi.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

class DomainMetrics(
    registry: MeterRegistry,
) {
    private val simulationDaysExecuted: Counter =
        Counter
            .builder("kanban.simulation.days.executed.total")
            .description("Total number of simulation days executed")
            .register(registry)

    fun recordSimulationDayExecuted() {
        simulationDaysExecuted.increment()
    }
}
