package com.kanbanvision.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditTeamSimulatorCoverageTest {
    @Test
    fun `team models expose audit`() {
        val audit = Audit(createdAt = Instant.parse("2026-03-01T00:00:00Z"))
        val ability = Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL, audit = audit)
        val worker = Worker(name = "worker", abilities = setOf(ability), audit = audit)
        val squad =
            Squad(
                name = "squad",
                workers = listOf(worker),
                audit = audit,
            )
        val tribe =
            Tribe(
                name = "tribe",
                squads = listOf(squad),
                audit = audit,
            )

        assertEquals(audit, ability.audit)
        assertEquals(audit, worker.audit)
        assertEquals(audit, squad.audit)
        assertEquals(audit, tribe.audit)
    }

    @Test
    fun `simulator ability and worker expose audit and date compatibility`() {
        val audit = Audit(createdAt = Instant.parse("2026-03-01T00:00:00Z"))
        val tester =
            Ability(
                name = AbilityName.TESTER,
                seniority = Seniority.PL,
                audit = audit,
            )
        val deployer =
            Ability(
                name = AbilityName.DEPLOYER,
                seniority = Seniority.PL,
                audit = audit,
            )
        val worker =
            Worker(
                name = "sim-worker",
                abilities = setOf(tester, deployer),
                audit = audit,
            )

        assertEquals(audit, tester.audit)
        assertEquals(audit.createdAt, tester.createdDate)
        assertEquals(audit, worker.audit)
        assertEquals(audit.updatedAt, worker.updatedDate)
        assertEquals(audit.deletedAt, worker.deletedDate)
    }

    @Test
    fun `simulator squad tribe and step expose audit`() {
        val audit = Audit(createdAt = Instant.parse("2026-03-01T00:00:00Z"))
        val squad = Squad(name = "sim-squad", workers = emptyList(), audit = audit)
        val tribe = Tribe(name = "sim-tribe", squads = listOf(squad), audit = audit)
        val step =
            Step(
                name = "sim-step",
                requiredAbility = AbilityName.TESTER,
                audit = audit,
            )

        assertEquals(audit, squad.audit)
        assertEquals(audit, tribe.audit)
        assertEquals(audit, step.audit)
    }

    @Test
    fun `simulator card exposes audit and compatibility date`() {
        val audit = Audit(createdAt = Instant.parse("2026-03-01T00:00:00Z"))
        val card =
            Card(
                title = "sim-card",
                analysisEffort = 1,
                developmentEffort = 1,
                testEffort = 1,
                deployEffort = 1,
                audit = audit,
            )

        assertEquals(audit, card.audit)
        assertEquals(audit.createdAt, card.createdDate)
    }
}
