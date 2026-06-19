package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.PolicySet
import com.kanbanvision.domain.model.organization.Scenario
import com.kanbanvision.domain.model.organization.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class SimulationConfigInvariantPropertyTest {
    @Test
    fun `Scenario create rejects any blank name`() {
        runBlocking {
            val rules = ScenarioRules.create(wipLimit = 3, teamSize = 2, seedValue = 0L)
            forAll(ARB_BLANK) { blank ->
                runCatching { Scenario.create(name = blank, rules = rules) }.isFailure
            }
        }
    }

    @Test
    fun `Scenario create accepts any non-blank name`() {
        runBlocking {
            val rules = ScenarioRules.create(wipLimit = 3, teamSize = 2, seedValue = 0L)
            forAll(ARB_NON_BLANK) { name ->
                runCatching { Scenario.create(name = name, rules = rules) }.isSuccess
            }
        }
    }

    @Test
    fun `SimulationDay rejects any value less than 1`() {
        runBlocking {
            forAll(Arb.int(NEG_BOUND..0)) { invalid ->
                runCatching { SimulationDay(invalid) }.isFailure
            }
        }
    }

    @Test
    fun `SimulationDay accepts any value of at least 1`() {
        runBlocking {
            forAll(Arb.int(1..POS_BOUND)) { valid ->
                runCatching { SimulationDay(valid) }.isSuccess
            }
        }
    }

    @Test
    fun `PolicySet rejects wipLimit of zero or negative`() {
        runBlocking {
            forAll(Arb.int(NEG_BOUND..0)) { invalid ->
                runCatching { PolicySet(wipLimit = invalid) }.isFailure
            }
        }
    }

    @Test
    fun `PolicySet accepts any positive wipLimit`() {
        runBlocking {
            forAll(Arb.int(1..POS_BOUND)) { valid ->
                runCatching { PolicySet(wipLimit = valid) }.isSuccess
            }
        }
    }

    @Test
    fun `Simulation create rejects any blank name`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching { Simulation.create(name = blank, organization = ORG, scenario = SCENARIO) }.isFailure
            }
        }
    }

    @Test
    fun `Simulation create accepts any non-blank name`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                runCatching { Simulation.create(name = name, organization = ORG, scenario = SCENARIO) }.isSuccess
            }
        }
    }

    private companion object {
        const val NAME_MAX = 50
        const val NEG_BOUND = -1000
        const val POS_BOUND = 10_000
        val ARB_BLANK: Arb<String> = Arb.of("", " ", "   ", "\t", "\n")
        val ARB_NON_BLANK: Arb<String> =
            Arb.string(minSize = 1, maxSize = NAME_MAX).filter { it.isNotBlank() }
        val ORG = Organization.create("Org")
        val RULES = ScenarioRules.create(wipLimit = 3, teamSize = 2, seedValue = 0L)
        val SCENARIO = Scenario.create(name = "Scenario", rules = RULES)
    }
}
