package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Domain
import com.kanbanvision.domain.model.SimulationRef
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.Scenario
import java.util.UUID

data class Simulation(
    override val id: String,
    val name: String,
    val currentDay: SimulationDay,
    val status: SimulationStatus,
    val organization: Organization,
    val scenario: Scenario,
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "Simulation id must not be blank" }
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
                id = UUID.randomUUID().toString(),
                name = name,
                currentDay = SimulationDay(1),
                status = status,
                organization = organization,
                scenario = scenario,
            )
        }
    }

    fun toRef(): SimulationRef = SimulationRef(id = id)

    fun withStatus(newStatus: SimulationStatus): Simulation = copy(status = newStatus)

    fun advanceDay(): Simulation = copy(currentDay = SimulationDay(currentDay.value + 1))
}
