package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Domain
import com.kanbanvision.domain.model.SimulationId
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.Scenario
import java.util.UUID

data class Simulation(
    override val id: SimulationId,
    val name: String,
    val currentDay: SimulationDay,
    val status: SimulationStatus,
    val organization: Organization,
    val scenario: Scenario,
    val decisions: List<Decision> = emptyList(),
    val history: List<DailySnapshot> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain<SimulationId> {
    init {
        require(name.isNotBlank()) { "Simulation name must not be blank" }
    }

    companion object {
        fun create(
            name: String,
            organization: Organization,
            scenario: Scenario,
            status: SimulationStatus = SimulationStatus.DRAFT,
        ): Simulation {
            require(name.isNotBlank()) { "Simulation name must not be blank" }
            return Simulation(
                id = SimulationId(UUID.randomUUID().toString()),
                name = name,
                currentDay = SimulationDay(1),
                status = status,
                organization = organization,
                scenario = scenario,
            )
        }
    }

    fun toRef(): SimulationId = id

    fun withStatus(newStatus: SimulationStatus): Simulation = copy(status = newStatus)

    fun advanceDay(): Simulation = copy(currentDay = SimulationDay(currentDay.value + 1))

    fun appendDecision(decision: Decision): Simulation = copy(decisions = decisions + decision)

    fun appendSnapshot(snapshot: DailySnapshot): Simulation = copy(history = history + snapshot)
}
