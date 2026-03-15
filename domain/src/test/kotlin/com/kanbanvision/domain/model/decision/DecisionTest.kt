package com.kanbanvision.domain.model.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DecisionTest {
    @Test
    fun `move factory creates MOVE_ITEM decision with workItemId payload`() {
        val d = Decision.move("item-1")
        assertEquals(DecisionType.MOVE_ITEM, d.type)
        assertEquals("item-1", d.payload["workItemId"])
        assertTrue(d.id.value.isNotBlank())
    }

    @Test
    fun `block factory creates BLOCK_ITEM decision with reason payload`() {
        val d = Decision.block("item-1", "waiting for review")
        assertEquals(DecisionType.BLOCK_ITEM, d.type)
        assertEquals("item-1", d.payload["workItemId"])
        assertEquals("waiting for review", d.payload["reason"])
    }

    @Test
    fun `unblock factory creates UNBLOCK_ITEM decision`() {
        val d = Decision.unblock("item-1")
        assertEquals(DecisionType.UNBLOCK_ITEM, d.type)
        assertEquals("item-1", d.payload["workItemId"])
    }

    @Test
    fun `addItem factory creates ADD_ITEM decision with title and serviceClass`() {
        val d = Decision.addItem("New feature", "EXPEDITE")
        assertEquals(DecisionType.ADD_ITEM, d.type)
        assertEquals("New feature", d.payload["title"])
        assertEquals("EXPEDITE", d.payload["serviceClass"])
    }

    @Test
    fun `DecisionId blank throws`() {
        assertFailsWith<IllegalArgumentException> { DecisionId("") }
    }
}
