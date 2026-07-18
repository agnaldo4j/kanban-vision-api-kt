package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.errors.SimulationError
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * OCP safety net for the [Decision] sealed hierarchy on the HTTP boundary.
 *
 * `DecisionRequest.toDomain` decodes the request's `type` `String` and therefore keeps a
 * fail-closed `else` (an unknown type returns `SimulationError.InvalidDecision`). That decode
 * cannot be made compiler-exhaustive over the sealed type. This test provides the missing
 * guarantee: the `when (original)` below is exhaustive over [Decision], so adding a new
 * variant stops this file compiling until the variant is added here — which then forces it
 * through `DecisionRequest.toDomain`, failing unless the decoder handles it.
 */
class DecisionRequestExhaustivenessTest {
    private val samples =
        listOf(
            Decision.MoveItem(CardId("c-1")),
            Decision.BlockItem(CardId("c-1"), "dep"),
            Decision.UnblockItem(CardId("c-1")),
            Decision.AddItem("t", ServiceClass.EXPEDITE),
        )

    @Test
    fun `every Decision variant decodes from its DecisionRequest wire form`() {
        samples.forEach { original ->
            val request =
                when (original) {
                    is Decision.MoveItem ->
                        DecisionRequest("MOVE_ITEM", mapOf("cardId" to original.cardId.value))
                    is Decision.BlockItem ->
                        DecisionRequest(
                            "BLOCK_ITEM",
                            mapOf("cardId" to original.cardId.value, "reason" to original.reason),
                        )
                    is Decision.UnblockItem ->
                        DecisionRequest("UNBLOCK_ITEM", mapOf("cardId" to original.cardId.value))
                    is Decision.AddItem ->
                        DecisionRequest(
                            "ADD_ITEM",
                            mapOf("title" to original.title, "serviceClass" to original.serviceClass.name),
                        )
                }
            val decoded = request.toDomain()
            assertTrue(decoded.isRight())
            assertEquals(original, decoded.getOrNull())
        }
    }

    @Test
    fun `blank cardId decodes to InvalidDecision instead of throwing`() {
        // Regression (GAP-BT): CardId's isNotBlank guard would throw → 500; must fold to a 400 error.
        listOf("MOVE_ITEM", "BLOCK_ITEM", "UNBLOCK_ITEM").forEach { type ->
            val decoded = DecisionRequest(type, mapOf("cardId" to "   ")).toDomain()
            assertTrue(decoded.isLeft(), "$type with blank cardId must be Left")
            assertIs<SimulationError.InvalidDecision>(decoded.leftOrNull())
        }
    }
}
