package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.model.CardId
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.Decision
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OCP safety net for the [Decision] sealed hierarchy on the persistence boundary.
 *
 * `DecisionSurrogate.toDomain` decodes a persisted `String` tag and therefore keeps a
 * fail-closed `else` (a corrupt blob must not be silently accepted). That decode cannot be
 * made compiler-exhaustive over the sealed type. This test provides the missing guarantee:
 * the `when (original)` below is exhaustive over [Decision], so adding a new variant stops
 * this file compiling until the variant is added here — which then forces it through the
 * surrogate round-trip (`toSurrogate().toDomain()`), failing unless the decoder handles it.
 */
class DecisionRoundTripExhaustivenessTest {
    private val samples =
        listOf(
            Decision.MoveItem(cardId = CardId("c-1")),
            Decision.BlockItem(cardId = CardId("c-1"), reason = "dep"),
            Decision.UnblockItem(cardId = CardId("c-1")),
            Decision.AddItem(title = "t", serviceClass = ServiceClass.EXPEDITE),
        )

    @Test
    fun `every Decision variant survives the surrogate round-trip`() {
        samples.forEach { original ->
            val expectedTag =
                when (original) {
                    is Decision.MoveItem -> "MOVE_ITEM"
                    is Decision.BlockItem -> "BLOCK_ITEM"
                    is Decision.UnblockItem -> "UNBLOCK_ITEM"
                    is Decision.AddItem -> "ADD_ITEM"
                }
            val surrogate = original.toSurrogate()
            assertEquals(expectedTag, surrogate.type)
            assertEquals(original, surrogate.toDomain())
        }
    }
}
