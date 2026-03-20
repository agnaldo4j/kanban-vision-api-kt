package com.kanbanvision.domain.model

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditCoreDomainCoverageTest {
    @Test
    fun `board card column and step expose audit`() {
        val audit = Audit(createdAt = Instant.parse("2026-02-01T00:00:00Z"))
        val board = Board(UUID.randomUUID().toString(), "Board", audit = audit)
        val column = Step(UUID.randomUUID().toString(), board.id, "Analysis", 0, AbilityName.PRODUCT_MANAGER, audit = audit)
        val card = Card(UUID.randomUUID().toString(), column.id, "Card", position = 0, audit = audit)
        val step = Step(UUID.randomUUID().toString(), board.id, "Dev", 1, AbilityName.DEVELOPER, audit = audit)

        assertEquals(audit, board.audit)
        assertEquals(audit.createdAt, board.createdAt)
        assertEquals(audit, column.audit)
        assertEquals(audit, card.audit)
        assertEquals(audit.createdAt, card.createdAt)
        assertEquals(audit, step.audit)
    }

    @Test
    fun `scenario and execution models expose audit`() {
        val audit = Audit(createdAt = Instant.parse("2026-02-01T00:00:00Z"))
        val config = ScenarioConfig(wipLimit = 2, teamSize = 2, seedValue = 10L, audit = audit)
        val scenario = scenarioWithAudit(config, audit)
        val state = simulationStateWithAudit(audit)
        val snapshot = snapshotWithAudit(scenario.id, audit)
        val result = SimulationResult(newState = state, snapshot = snapshot, audit = audit)

        assertEquals(audit, config.audit)
        assertEquals(audit, scenario.audit)
        assertEquals(audit, state.policySet.audit)
        assertEquals(audit, state.cards.first().audit)
        assertEquals(audit, state.audit)
        assertEquals(audit, snapshot.metrics.audit)
        assertEquals(audit, snapshot.movements.first().audit)
        assertEquals(audit, snapshot.audit)
        assertEquals(audit, result.audit)
    }

    @Test
    fun `decision and tenant copies keep audit`() {
        val audit = Audit(createdAt = Instant.parse("2026-02-01T00:00:00Z"))
        val decision = Decision.move("work-item-id").copy(audit = audit)
        val tenant = Tenant.create("Another").copy(audit = audit)

        assertEquals(audit, decision.audit)
        assertEquals(audit, tenant.audit)
    }

    private fun scenarioWithAudit(
        config: ScenarioConfig,
        audit: Audit,
    ): Scenario {
        val tenant = Tenant.create("Tenant")
        return Scenario.create(tenantId = tenant.id, config = config).copy(audit = audit)
    }

    private fun simulationStateWithAudit(audit: Audit): SimulationState {
        val item =
            WorkItem(
                id = UUID.randomUUID().toString(),
                title = "WI",
                serviceClass = ServiceClass.STANDARD,
                state = WorkItemState.TODO,
                agingDays = 0,
                audit = audit,
            )
        return SimulationState(
            currentDay = SimulationDay(1),
            cards = listOf(item),
            policySet = PolicySet(wipLimit = 2, audit = audit),
            audit = audit,
        )
    }

    private fun snapshotWithAudit(
        scenarioId: String,
        audit: Audit,
    ): DailySnapshot {
        val metrics = FlowMetrics(throughput = 1, wipCount = 1, blockedCount = 0, avgAgingDays = 1.0, audit = audit)
        val movement = Movement(MovementType.MOVED, UUID.randomUUID().toString(), SimulationDay(1), "moved", audit = audit)
        return DailySnapshot(scenarioId, SimulationDay(1), metrics, listOf(movement), audit = audit)
    }
}
