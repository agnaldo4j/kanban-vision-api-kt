package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.organization.Scenario
import com.kanbanvision.domain.model.simulation.Simulation

data class BoardRef(
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "BoardRef id must not be blank" }
    }
}

data class StepRef(
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "StepRef id must not be blank" }
    }
}

data class SimulationRef(
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "SimulationRef id must not be blank" }
    }
}

data class ScenarioRef(
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "ScenarioRef id must not be blank" }
    }
}

fun Board.toRef(): BoardRef = BoardRef(id = id)

fun Step.toRef(): StepRef = StepRef(id = id)

fun Simulation.toRef(): SimulationRef = SimulationRef(id = id)

fun Scenario.toRef(): ScenarioRef = ScenarioRef(id = id)
