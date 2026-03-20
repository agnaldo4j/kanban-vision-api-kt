package com.kanbanvision.domain.model

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DomainInvariantRulesTest {
    @Test
    fun `given domain errors and audit factory when instantiated then fields are preserved`() {
        assertEquals("b-1", DomainError.BoardNotFound("b-1").id)
        assertEquals("c-1", DomainError.CardNotFound("c-1").id)
        assertEquals("s-1", DomainError.StepNotFound("s-1").id)
        assertEquals("org-1", DomainError.OrganizationNotFound("org-1").id)
        assertEquals("sim-1", DomainError.SimulationNotFound("sim-1").id)
        assertEquals("broken", DomainError.PersistenceError("broken").message)
        assertEquals("reason", DomainError.InvalidDecision("reason").reason)
        val audit = Audit.now()
        assertTrue(audit.updatedAt >= audit.createdAt)
    }

    @Test
    fun `given team structures when required names are blank then invariant validation fails`() {
        val squad = Squad(name = "Squad", workers = listOf(devWorker()))
        val tribe = Tribe(name = "Tribe", squads = listOf(squad))

        assertEquals("Squad", squad.name)
        assertEquals("Tribe", tribe.name)

        assertFailsWith<IllegalArgumentException> { Squad(name = "", workers = emptyList()) }
        assertFailsWith<IllegalArgumentException> { Tribe(name = "", squads = emptyList()) }
    }

    @Test
    fun `given decision factories when building commands then payload contracts are consistent`() {
        val move = Decision.move("card-1")
        val block = Decision.block("card-1", "dep")
        val unblock = Decision.unblock("card-1")
        val add = Decision.addItem("New", "EXPEDITE")

        assertEquals(DecisionType.MOVE_ITEM, move.type)
        assertEquals("dep", block.payload["reason"])
        assertEquals(DecisionType.UNBLOCK_ITEM, unblock.type)
        assertEquals("EXPEDITE", add.payload["serviceClass"])
    }

    @Test
    fun `given card effort consumption when points are applied then remaining effort is updated`() {
        assertFailsWith<IllegalArgumentException> { Card(id = "", stepId = "s", title = "t") }
        assertFailsWith<IllegalArgumentException> { Card(stepId = "", title = "t") }
        assertFailsWith<IllegalArgumentException> { Card(stepId = "s", title = "") }
        assertFailsWith<IllegalArgumentException> { Card(stepId = "s", title = "t", position = -1) }

        val card = Card(stepId = "s", title = "t", analysisEffort = 1, developmentEffort = 1, testEffort = 1, deployEffort = 1)
        val consumed =
            card
                .consumeEffort(AbilityName.PRODUCT_MANAGER, 1)
                .consumeEffort(AbilityName.DEVELOPER, 1)
                .consumeEffort(AbilityName.TESTER, 1)
                .consumeEffort(AbilityName.DEPLOYER, 1)

        assertEquals(0, consumed.remainingAnalysisEffort)
        assertEquals(0, consumed.remainingDevelopmentEffort)
        assertEquals(0, consumed.remainingTestEffort)
        assertEquals(0, consumed.remainingDeployEffort)

        assertFailsWith<IllegalArgumentException> {
            Card(stepId = "s", title = "t").consumeEffort(AbilityName.DEVELOPER, -1)
        }
    }

    private fun devWorker(): Worker =
        Worker(
            name = "Dev",
            abilities =
                setOf(
                    Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL),
                    Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                ),
        )
}
