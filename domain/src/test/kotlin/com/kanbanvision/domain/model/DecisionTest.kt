package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DecisionTest {
    @Test
    fun `move factory creates MOVE_ITEM decision with cardId payload`() {
        val d = Decision.move("item-1")
        assertEquals(DecisionType.MOVE_ITEM, d.type)
        assertEquals("item-1", d.payload["cardId"])
        assertTrue(d.id.isNotBlank())
    }

    @Test
    fun `block factory creates BLOCK_ITEM decision with reason payload`() {
        val d = Decision.block("item-1", "waiting for review")
        assertEquals(DecisionType.BLOCK_ITEM, d.type)
        assertEquals("item-1", d.payload["cardId"])
        assertEquals("waiting for review", d.payload["reason"])
    }

    @Test
    fun `unblock factory creates UNBLOCK_ITEM decision`() {
        val d = Decision.unblock("item-1")
        assertEquals(DecisionType.UNBLOCK_ITEM, d.type)
        assertEquals("item-1", d.payload["cardId"])
    }

    @Test
    fun `addItem factory creates ADD_ITEM decision with title and serviceClass`() {
        val d = Decision.addItem("New feature", "EXPEDITE")
        assertEquals(DecisionType.ADD_ITEM, d.type)
        assertEquals("New feature", d.payload["title"])
        assertEquals("EXPEDITE", d.payload["serviceClass"])
    }

    @Test
    fun `decision with blank id throws`() {
        assertFailsWith<IllegalArgumentException> {
            Decision(id = "", type = DecisionType.MOVE_ITEM, payload = emptyMap())
        }
    }

    @Test
    fun `decision factories consistently use cardId payload`() {
        val move = Decision.move("item-1")
        val block = Decision.block("item-1", "legacy")
        val unblock = Decision.unblock("item-1")

        assertEquals(DecisionType.MOVE_ITEM, move.type)
        assertEquals(DecisionType.BLOCK_ITEM, block.type)
        assertEquals(DecisionType.UNBLOCK_ITEM, unblock.type)
        assertEquals("item-1", move.payload["cardId"])
        assertEquals("item-1", block.payload["cardId"])
        assertEquals("legacy", block.payload["reason"])
        assertEquals("item-1", unblock.payload["cardId"])
    }
}
