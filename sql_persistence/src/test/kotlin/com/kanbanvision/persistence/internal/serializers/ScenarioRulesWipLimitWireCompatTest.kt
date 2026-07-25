package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * `ScenarioRules.wipLimit` is a delegating accessor over `policySet.wipLimit` (GAP-DO), but the
 * surrogate still writes the redundant `rules.wipLimit` key so a pod running an older release can
 * decode blobs written by this one during a rolling deploy. These tests pin both halves of that
 * contract: the key is still emitted, and decode ignores it.
 */
class ScenarioRulesWipLimitWireCompatTest {
    @Test
    fun `given encoded simulation when reading the rules blob then the legacy wip limit key is still emitted`() {
        val encoded = SimulationSerializer.encode(PersistenceFixtures.simulation())

        val rules = rulesObject(encoded)
        val policySetWipLimit = assertNotNull(rules["policySet"]).jsonObject["wipLimit"]
        val legacyWipLimit = assertNotNull(rules["wipLimit"], "rules.wipLimit must stay on the wire")

        assertEquals(2, legacyWipLimit.jsonPrimitive.int)
        assertEquals(assertNotNull(policySetWipLimit).jsonPrimitive.int, legacyWipLimit.jsonPrimitive.int)
    }

    @Test
    fun `given legacy blob whose rules wip limit diverges from the policy set when decoding then policy set wins`() {
        val encoded = SimulationSerializer.encode(PersistenceFixtures.simulation())
        val divergent = encoded.replace(""","wipLimit":2,"teamSize":2,""", ""","wipLimit":99,"teamSize":2,""")
        assertNotEquals(encoded, divergent, "the divergent blob must actually differ, otherwise this test is vacuous")

        val decoded = SimulationSerializer.decode(divergent)

        assertEquals(2, decoded.scenario.rules.wipLimit)
        assertEquals(2, decoded.scenario.rules.policySet.wipLimit)
    }

    private fun rulesObject(encoded: String) =
        assertNotNull(
            assertNotNull(Json.parseToJsonElement(encoded).jsonObject["scenario"]).jsonObject["rules"],
        ).jsonObject
}
