package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import com.kanbanvision.domain.common.model.NonBlankName
import com.kanbanvision.domain.model.organization.Organization
import java.util.UUID

data class Simulation(
    override val id: SimulationId,
    val name: NonBlankName,
    val currentDay: SimulationDay,
    val status: SimulationStatus,
    val organization: Organization,
    val scenario: Scenario,
    val decisions: List<Decision> = emptyList(),
    val history: List<DailySnapshot> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain<SimulationId> {
    companion object {
        private const val DEFAULT_SCENARIO_NAME = "Default Simulation Scenario"
        private const val NAME_ID_PREFIX_LENGTH = 8

        fun create(
            name: String,
            organization: Organization,
            scenario: Scenario,
            status: SimulationStatus = SimulationStatus.DRAFT,
        ): Simulation =
            Simulation(
                id = SimulationId(UUID.randomUUID().toString()),
                name = NonBlankName(name),
                currentDay = SimulationDay(1),
                status = status,
                organization = organization,
                scenario = scenario,
            )

        fun draftFor(
            organization: Organization,
            rules: ScenarioRules,
        ): Simulation {
            val id = SimulationId(UUID.randomUUID().toString())
            return Simulation(
                id = id,
                name = NonBlankName("Simulation ${id.value.take(NAME_ID_PREFIX_LENGTH)}"),
                currentDay = SimulationDay(1),
                status = SimulationStatus.DRAFT,
                organization = organization,
                scenario = Scenario.create(name = DEFAULT_SCENARIO_NAME, rules = rules),
            )
        }
    }

    fun itemCount(): Int = scenario.itemCount()

    fun toRef(): SimulationId = id

    fun withStatus(newStatus: SimulationStatus): Simulation = copy(status = newStatus)

    fun advanceDay(): Simulation = copy(currentDay = SimulationDay(currentDay.value + 1))

    fun appendDecision(decision: Decision): Simulation = copy(decisions = decisions + decision)

    // Uma única concatenação, O(atual + lote): dobrar `appendDecision` sobre o lote seria O(n²).
    fun appendDecisions(newDecisions: List<Decision>): Simulation = copy(decisions = decisions + newDecisions)

    fun appendSnapshot(snapshot: DailySnapshot): Simulation = copy(history = history + snapshot)
}
