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
 * `DecisionSurrogate.toDomain` decodes a persisted `String` tag, so it cannot be made
 * compiler-exhaustive over the sealed type. Since GAP-DS its `else` is deliberately *not*
 * fail-closed: the blob is an immutable read record, and throwing there would make the whole
 * aggregate unloadable rather than degrade one decision — so an unrecognised tag becomes
 * [Decision.Unknown], which round-trips back to storage unchanged.
 *
 * That makes this test the only compiler-enforced guarantee left: the `when (original)` below is
 * exhaustive over [Decision], so adding a new variant stops this file compiling until the variant
 * is added here — which then forces it through the surrogate round-trip (`toSurrogate().toDomain()`),
 * failing unless the decoder handles it. Without this, a new variant would encode fine and silently
 * decode back as [Decision.Unknown].
 */
class DecisionRoundTripExhaustivenessTest {
    private val samples =
        listOf(
            Decision.MoveItem(cardId = CardId("c-1")),
            Decision.BlockItem(cardId = CardId("c-1"), reason = "dep"),
            Decision.UnblockItem(cardId = CardId("c-1")),
            Decision.AddItem(title = NonBlankTitle("t"), serviceClass = ServiceClass.EXPEDITE),
            Decision.Unknown(type = "FUTURE_KIND", payload = mapOf("k" to "v")),
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
                    is Decision.Unknown -> original.type
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
