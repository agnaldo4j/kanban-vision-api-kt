package com.kanbanvision.domain.model

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GuardAndDefaultsTest {
    @Test
    fun `given domain errors when accessing getters and secondary constructors then contracts remain stable`() {
        val validation = DomainError.ValidationError("single")
        assertEquals(listOf("single"), validation.messages)
        assertEquals("single", validation.message)

        val executed = DomainError.DayAlreadyExecuted(2)
        assertEquals(2, executed.day)
    }

    @Test
    fun `given invalid worker definitions when constructing then guard clauses fail fast`() {
        val tester =
            Ability(
                name = AbilityName.TESTER,
                seniority = Seniority.JR,
            )
        assertFailsWith<IllegalArgumentException> { Worker(name = "Tester", abilities = setOf(tester)) }
        assertFailsWith<IllegalArgumentException> { Worker(id = "", name = "Dev", abilities = setOf(developer(), deployer())) }
        assertFailsWith<IllegalArgumentException> { Worker(name = "", abilities = setOf(developer(), deployer())) }
        assertFailsWith<IllegalArgumentException> { Worker(name = "Dev", abilities = emptySet()) }

        val worker = Worker(name = "Dev", abilities = setOf(developer(), deployer()))
        val analysisStep = Step.create("b-1", "Analysis", 1, AbilityName.PRODUCT_MANAGER)
        assertTrue(!worker.canExecute(analysisStep))
    }

    @Test
    fun `given decision defaults when factories are called then payload defaults are applied`() {
        assertFailsWith<IllegalArgumentException> { Decision(id = "", type = DecisionType.MOVE_ITEM, payload = emptyMap()) }

        val decision = Decision.move("c-1")
        val blockWithDefaultReason = Decision.block("c-1")
        val addWithDefaultServiceClass = Decision.addItem("new")
        assertTrue(decision.component1().isNotBlank())
        assertEquals(DecisionType.MOVE_ITEM, decision.component2())
        assertEquals("c-1", decision.component3()["cardId"])
        assertEquals(decision.component4(), decision.copy().audit)
        assertEquals("blocked", blockWithDefaultReason.payload["reason"])
        assertEquals("STANDARD", addWithDefaultServiceClass.payload["serviceClass"])
    }

    @Test
    fun `given step execution result and factory when used then data contract and validations hold`() {
        assertFailsWith<IllegalArgumentException> {
            Step.create(boardId = "", name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(boardId = "b-1", name = "", position = 0, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(boardId = "b-1", name = "Dev", position = -1, requiredAbility = AbilityName.DEVELOPER)
        }

        val card = Card(stepId = "s-1", title = "Card")
        val result = Step.ExecutionResult(updatedCard = card, consumedEffort = 1, isStepCompleted = false)
        assertEquals(card.id, result.component1().id)
        assertEquals(1, result.component2())
        assertEquals(false, result.component3())
        assertTrue(result.toString().contains("ExecutionResult"))
    }

    private fun developer(): Ability = Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)

    private fun deployer(): Ability = Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL)
}
