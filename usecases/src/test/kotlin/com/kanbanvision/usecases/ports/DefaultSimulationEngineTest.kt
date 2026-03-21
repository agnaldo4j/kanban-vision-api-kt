package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.Decision
import com.kanbanvision.usecases.simulation.fixtureSimulation
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultSimulationEngineTest {
    private val engine = DefaultSimulationEngine()

    @Test
    fun `given simulation and deterministic seed when running day through default engine then simulation day advances`() {
        val simulation = fixtureSimulation(id = "sim-1", day = 1)

        val result = engine.runDay(simulation = simulation, decisions = listOf(Decision.addItem("Card 1")), seed = 42L)

        assertEquals(2, result.simulation.currentDay.value)
        assertEquals(1, result.snapshot.day.value)
        assertEquals(simulation.id, result.snapshot.simulation.id)
    }
}
