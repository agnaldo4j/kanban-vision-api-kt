package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
            Decision.AddItem(title = NonBlankTitle("t"), serviceClass = ServiceClass.EXPEDITE),
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

    @Test
    fun `legacy blank add-item title decodes to a sentinel instead of crashing the load`() {
        // Backward-compat (GAP-DH): um blob pré-GAP-DH podia ter AddItem com título em branco. O decode deve
        // permanecer carregável (não lançar) para não tornar a simulação inteira ilegível via findById/findAll.
        val legacy = DecisionSurrogate(type = "ADD_ITEM", payload = mapOf("title" to "", "serviceClass" to "STANDARD"))

        val decoded = legacy.toDomain()

        val addItem = assertIs<Decision.AddItem>(decoded)
        assertEquals("(untitled)", addItem.title.value)
    }
}
